package parsley.internal.errors

import ParseError.Unknown
import Raw.Unprintable
import scala.util.matching.Regex

private [internal] sealed abstract class ParseError {
    val offset: Int
    val col: Int
    val line: Int

    def withHints(hints: Set[ErrorItem]): ParseError
    def giveReason(reason: String): ParseError
    def pretty(sourceName: Option[String])(implicit helper: LineBuilder): String

    protected final def posStr(sourceName: Option[String]): String = {
        val scopeName = sourceName.fold("")(name => s"In file '$name' ")
        s"$scopeName(line $line, column $col)"
    }

    protected final def disjunct(alts: List[String]): Option[String] = alts.sorted.reverse.filter(_.nonEmpty) match {
        case Nil => None
        case List(alt) => Some(alt)
        case List(alt1, alt2) => Some(s"$alt2 or $alt1")
        // If the result would contains "," then it's probably nicer to preserve any potential grouping using ";"
        case any@(alt::alts) if any.exists(_.contains(",")) => Some(s"${alts.reverse.mkString("; ")}; or $alt")
        case alt::alts => Some(s"${alts.reverse.mkString(", ")}, or $alt")
    }

    protected final def assemble(sourceName: Option[String], infoLines: List[String])(implicit helper: LineBuilder): String = {
        val topStr = posStr(sourceName)
        val (line, caret) = helper.getLineWithCaret(offset)
        val info = infoLines.filter(_.nonEmpty).mkString("\n  ")
        s"$topStr:\n  ${if (info.isEmpty) Unknown else info}\n  >${line}\n  >${caret}"
    }
}
// The reasons here are lightweight, two errors can merge their messages, but messages do not get converted to hints
private [internal] case class TrivialError(offset: Int, line: Int, col: Int,
                                           unexpected: Option[ErrorItem], expecteds: Set[ErrorItem], reasons: Set[String])
    extends ParseError {
    def withHints(hints: Set[ErrorItem]): ParseError = copy(expecteds = expecteds union hints)
    def giveReason(reason: String): ParseError = copy(reasons = reasons + reason)

    def pretty(sourceName: Option[String])(implicit helper: LineBuilder): String = {
        assemble(sourceName, List(unexpectedInfo, expectedInfo).flatten ::: reasons.toList)
    }

    private def unexpectedInfo: Option[String] = unexpected.map(u => s"unexpected ${u.msg}")
    private def expectedInfo: Option[String] = disjunct(expecteds.map(_.msg).toList).map(es => s"expected $es")
}
private [internal] case class FailError(offset: Int, line: Int, col: Int, msgs: Set[String]) extends ParseError {
    def withHints(hints: Set[ErrorItem]): ParseError = this
    def giveReason(reason: String): ParseError = this
    def pretty(sourceName: Option[String])(implicit helper: LineBuilder): String = {
        assemble(sourceName, msgs.toList)
    }
}

private [internal] object ParseError {
    val Unknown = "unknown parse error"
    val NoReason = Set.empty[String]
    val NoItems = Set.empty[ErrorItem]
}

private [internal] sealed trait ErrorItem {
    val msg: String
}
private [internal] object ErrorItem {
    def higherPriority(e1: ErrorItem, e2: ErrorItem): ErrorItem = (e1, e2) match {
        case (EndOfInput, _) => EndOfInput
        case (_, EndOfInput) => EndOfInput
        case (e: Desc, _) => e
        case (_, e: Desc) => e
        case (Raw(r1), Raw(r2)) => if (r1.length >= r2.length) e1 else e2
    }
}
private [internal] case class Raw(cs: String) extends ErrorItem {
    // This could be marked threadUnsafe in Scala 3?
    override lazy val msg = cs match {
        case "\n"            => "newline"
        case "\t"            => "tab"
        case " "             => "space"
        case Unprintable(up) => f"unprintable character (\\u${up.head.toInt}%04X)"
        // Do we want this only in unexpecteds?
        case cs              => "\"" + cs.takeWhile(c => c != '\n' && c != ' ') + "\""
    }
}
private [internal] object Raw {
    val Unprintable: Regex = "(\\p{C})".r
    def apply(c: Char): Raw = new Raw(s"$c")
}
private [internal] case class Desc(msg: String) extends ErrorItem
private [internal] case object EndOfInput extends ErrorItem {
    override val msg = "end of input"
}