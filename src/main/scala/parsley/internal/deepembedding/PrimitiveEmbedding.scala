package parsley.internal.deepembedding

import ContOps.{result, ContAdapter}
import parsley.internal.machine.instructions
import parsley.registers.Reg
import parsley.debug.{Breakpoint, EntryBreak, FullBreak, ExitBreak}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.language.higherKinds

private [parsley] final class Satisfy(private [Satisfy] val f: Char => Boolean, val expected: Option[String] = None)
    extends SingletonExpect[Char]("satisfy(f)", new Satisfy(f, _), new instructions.Satisfies(f, expected))

private [deepembedding] sealed abstract class ScopedUnary[A, B](_p: =>Parsley[A], name: String, doesNotProduceHints: Boolean,
                                                                empty: Option[String] => ScopedUnary[A, B], instr: instructions.Instr)
    extends Unary[A, B](_p)(c => s"$name($c)", empty) {
    final override val numInstrs = 2
    final override def codeGen[Cont[_, +_]: ContOps](implicit instrs: InstrBuffer, state: CodeGenState): Cont[Unit, Unit] = {
        val handler = state.freshLabel()
        instrs += new instructions.PushHandlerAndState(handler, doesNotProduceHints, doesNotProduceHints)
        p.codeGen |> {
            instrs += new instructions.Label(handler)
            instrs += instr
        }
    }
}
private [parsley] final class Attempt[A](_p: =>Parsley[A]) extends ScopedUnary[A, A](_p, "attempt", false, _ => Attempt.empty, instructions.Attempt)
private [parsley] final class Look[A](_p: =>Parsley[A]) extends ScopedUnary[A, A](_p, "lookAhead", true, _ => Look.empty, instructions.Look)
private [parsley] final class NotFollowedBy[A](_p: =>Parsley[A], val expected: Option[String] = None)
    extends ScopedUnary[A, Unit](_p, "notFollowedBy", true, NotFollowedBy.empty, new instructions.NotFollowedBy(expected)) {
    override def optimise: Parsley[Unit] = p match {
        case z: MZero => new Pure(())
        case _ => this
    }
}

private [parsley] final class Fail(private [Fail] val msg: String)
    extends SingletonExpect[Nothing](s"fail($msg)", _ => new Fail(msg), new instructions.Fail(msg)) with MZero

private [parsley] final class Unexpected(private [Unexpected] val msg: String, val expected: Option[String] = None)
    extends SingletonExpect[Nothing](s"unexpected($msg)", new Unexpected(msg, _), new instructions.Unexpected(msg, expected)) with MZero

private [parsley] final class Rec[A](val p: Parsley[A])
    extends SingletonExpect[A](s"rec $p", _ => new Rec(p), new instructions.Call(p.instrs))

private [parsley] final class Subroutine[A](var p: Parsley[A], val expected: Option[String]) extends Parsley[A] {
    // $COVERAGE-OFF$
    override def findLetsAux[Cont[_, +_]: ContOps](implicit seen: Set[Parsley[_]], state: LetFinderState, label: Option[String]): Cont[Unit, Unit] = {
        throw new Exception("Subroutines cannot exist during let detection")
    }
    // $COVERAGE-ON$
    override def preprocess[Cont[_, +_]: ContOps, A_ >: A](implicit seen: Set[Parsley[_]], sub: SubMap,
                                                           label: Option[String]): Cont[Unit, Parsley[A_]] = {
        // The idea here is that the label itself was already established by letFinding, so we just use expected which should be equal to label
        assert(expected == label, "letFinding should have already set the expected label for a subroutine")
        for (p <- this.p.optimised) yield this.ready(p)
    }
    private def ready(p: Parsley[A]): this.type = {
        this.p = p
        processed = true
        this
    }
    override def optimise: Parsley[A] = if (p.size <= 1) p else this // This threshold might need tuning?
    override def codeGen[Cont[_, +_]: ContOps](implicit instrs: InstrBuffer, state: CodeGenState): Cont[Unit, Unit] = {
        result(instrs += new instructions.GoSub(state.getSubLabel(this)))
    }
    // $COVERAGE-OFF$
    override def prettyASTAux[Cont[_, +_]: ContOps]: Cont[String, String] = result(s"Sub($p, $expected)")
    // $COVERAGE-ON$
}

private [parsley] object Line extends Singleton[Int]("line", instructions.Line)
private [parsley] object Col extends Singleton[Int]("col", instructions.Col)
private [parsley] final class Get[S](val reg: Reg[S]) extends Singleton[S](s"get($reg)", new instructions.Get(reg.addr)) with UsesRegister
private [parsley] final class Put[S](val reg: Reg[S], _p: =>Parsley[S])
    extends Unary[S, Unit](_p)(c => s"put($reg, $c)", _ => Put.empty(reg)) with UsesRegister {
    override val numInstrs = 1
    override def codeGen[Cont[_, +_]: ContOps](implicit instrs: InstrBuffer, state: CodeGenState): Cont[Unit, Unit] = {
        p.codeGen |>
        (instrs += new instructions.Put(reg.addr))
    }
}

private [parsley] final class ErrorLabel[A](_p: =>Parsley[A], label: String)
    extends Unary[A, A](_p)(c => s"$c.label($label)", expected =>
        ErrorLabel.empty(expected match {
            case None => label
            case Some(e) =>
                println(s"""WARNING: a label "$label" has been forcibly overriden by an unsafeLabel "$e" that encapsulates it.
                        |This is likely a mistake: confirm your intent by removing this label!""".stripMargin)
                e
        })) {
    final override val numInstrs = 2
    final override def codeGen[Cont[_, +_]: ContOps](implicit instrs: InstrBuffer, state: CodeGenState): Cont[Unit, Unit] = {
        val handler = state.freshLabel()
        instrs += new instructions.InputCheck(handler, true)
        p.codeGen |> {
            instrs += new instructions.Label(handler)
            instrs += new instructions.ApplyError(label)
        }
    }
}
private [parsley] final class ErrorExplain[A](_p: =>Parsley[A], reason: String)
    extends Unary[A, A](_p)(c => s"$c.explain($reason)", _ => ErrorExplain.empty(reason)) {
    final override val numInstrs = 2
    final override def codeGen[Cont[_, +_]: ContOps](implicit instrs: InstrBuffer, state: CodeGenState): Cont[Unit, Unit] = {
        val handler = state.freshLabel()
        instrs += new instructions.InputCheck(handler)
        p.codeGen |> {
            instrs += new instructions.Label(handler)
            instrs += new instructions.ApplyReason(reason)
        }
    }
}

private [parsley] final class UnsafeErrorRelabel[+A](_p: =>Parsley[A], msg: String) extends Parsley[A] {
    lazy val p = _p
    override def preprocess[Cont[_, +_]: ContOps, A_ >: A](implicit seen: Set[Parsley[_]], sub: SubMap,
                                                           label: Option[String]): Cont[Unit, Parsley[A_]] = {
        if (label.isEmpty) p.optimised(implicitly[ContOps[Cont]], seen, sub, Some(msg))
        else p.optimised
    }
    override def findLetsAux[Cont[_, +_]: ContOps](implicit seen: Set[Parsley[_]], state: LetFinderState, label: Option[String]): Cont[Unit, Unit] = {
        if (label.isEmpty) p.findLets(implicitly[ContOps[Cont]], seen, state, Some(msg))
        else p.findLets
    }
    // $COVERAGE-OFF$
    override def optimise: Parsley[A] = throw new Exception("Error relabelling should not be in optimisation!")
    override def codeGen[Cont[_, +_]: ContOps](implicit instrs: InstrBuffer, state: CodeGenState): Cont[Unit, Unit] = {
        throw new Exception("Error relabelling should not be in code gen!")
    }
    override def prettyASTAux[Cont[_, +_]: ContOps]: Cont[String, String] = for (c <- p.prettyASTAux) yield s"($c ? $msg)"
    // $COVERAGE-ON$
}
private [parsley] final class Debug[A](_p: =>Parsley[A], name: String, break: Breakpoint)
    extends Unary[A, A](_p)(identity[String], _ => Debug.empty(name, break)) {
    override val numInstrs = 2
    override def codeGen[Cont[_, +_]: ContOps](implicit instrs: InstrBuffer, state: CodeGenState): Cont[Unit, Unit] = {
        val handler = state.freshLabel()
        instrs += new instructions.LogBegin(handler, name, (break eq EntryBreak) || (break eq FullBreak))
        p.codeGen |> {
            instrs += new instructions.Label(handler)
            instrs += new instructions.LogEnd(name, (break eq ExitBreak) || (break eq FullBreak))
        }
    }
}

private [deepembedding] object Satisfy {
    def unapply(self: Satisfy): Option[Char => Boolean] = Some(self.f)
}
private [deepembedding] object Attempt {
    def empty[A]: Attempt[A] = new Attempt(???)
    def unapply[A](self: Attempt[A]): Option[Parsley[A]] = Some(self.p)
}
private [deepembedding] object Look {
    def empty[A]: Look[A] = new Look(???)
}
private [deepembedding] object ErrorLabel {
    def empty[A](label: String): ErrorLabel[A] = new ErrorLabel(???, label)
    def apply[A](p: Parsley[A], label: String): ErrorLabel[A] = empty(label).ready(p)
}
private [deepembedding] object ErrorExplain {
    def empty[A](reason: String): ErrorExplain[A] = new ErrorExplain(???, reason)
    def apply[A](p: Parsley[A], reason: String): ErrorExplain[A] = empty(reason).ready(p)
}
private [deepembedding] object NotFollowedBy {
    def empty[A](expected: Option[String]): NotFollowedBy[A] = new NotFollowedBy(???, expected)
}
private [deepembedding] object Put {
    def empty[S](r: Reg[S]): Put[S] = new Put(r, ???)
}
private [deepembedding] object Debug {
    def empty[A](name: String, break: Breakpoint): Debug[A] = new Debug(???, name, break)
}

private [deepembedding] object Rec {
    def apply[A](p: Parsley[A], expected: Option[String]): Parsley[A] = expected match {
        case None => new Rec(p)
        case Some(expected) => ErrorLabel(new Rec(p), expected)
    }
}