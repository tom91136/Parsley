package parsley.errors

import parsley.Parsley
import parsley.internal.deepembedding

import parsley.combinator.choice

/** This module contains combinators that can be used to directly influence error messages of parsers.
  * @since 3.0.0
  */
object combinator {
    /**
      * The `fail(msgs)` parser consumes no input and fails with `msg` as the error message
      * @since 3.0.0
      */
    def fail(msgs: String*): Parsley[Nothing] = choice(msgs.map(msg => new Parsley(new deepembedding.Fail(msg))): _*)

    /**
      * The `unexpected(msg)` parser consumes no input and fails with `msg` as an unexpected error
      * @since 3.0.0
      */
    def unexpected(msg: String): Parsley[Nothing] = new Parsley(new deepembedding.Unexpected(msg))

    implicit final class ErrorMethods[P, +A](p: =>P)(implicit con: P => Parsley[A]) {
        /** Filter the value of a parser; if the value returned by the parser is defined for the given partial function, then
          * the `filterOut` fails, using the result of the function as the ''reason'' (see [[explain]]), otherwise the parser
          * succeeds
          * @param pred The predicate that is tested against the parser result
          * @return The result of the invokee if the value failed the predicate
          * @since 3.0.0
          */
        def filterOut(pred: PartialFunction[A, String]): Parsley[A] = new Parsley(new deepembedding.FilterOut(p.internal, pred))
        /** Attempts to first filter the parser to ensure that `pf` is defined over it. If it is, then the function `pf`
          * is mapped over its result. Roughly the same as a `guard` then a `map`.
          * @param pf The partial function
          * @param msg The message used for the error if the input failed the check
          * @return The result of applying `pf` to this parsers value (if possible), or fails
          * @since 3.0.0
          */
        def collectMsg[B](msg: String)(pf: PartialFunction[A, B]): Parsley[B] = this.guardAgainst{case x if !pf.isDefinedAt(x) => msg}.map(pf)
        /** Attempts to first filter the parser to ensure that `pf` is defined over it. If it is, then the function `pf`
          * is mapped over its result. Roughly the same as a `guard` then a `map`.
          * @param pf The partial function
          * @param msggen Generator function for error message, generating a message based on the result of the parser
          * @return The result of applying `pf` to this parsers value (if possible), or fails
          * @since 3.0.0
          */
        def collectMsg[B](msggen: A => String)(pf: PartialFunction[A, B]): Parsley[B] = this.guardAgainst{case x if !pf.isDefinedAt(x) => msggen(x)}.map(pf)
        /** Similar to `filterOut`, except the error message generated yields a ''true failure''. This means that it will
          * uses the same mechanism as [[Parsley.fail]], as opposed to the reason provided by [[filterOut]]
          * @param pred The predicate that is tested against the parser result and produces error messages
          * @return The result of the invokee if it fails the predicate
          * @since 2.8.0
          */
        def guardAgainst(pred: PartialFunction[A, String]): Parsley[A] = new Parsley(new deepembedding.GuardAgainst(p.internal, pred))
        /** Alias for `label`
          * @since 3.0.0 */
        def ?(msg: String): Parsley[A] = this.label(msg)
        /** Sets the expected message for a parser. If the parser fails then `expected msg` will added to the error
          * @since 3.0.0 */
        def label(msg: String): Parsley[A] = new Parsley(new deepembedding.ErrorLabel(p.internal, msg))
        /** Similar to `label`, except instead of providing an expected message replacing the original tag, this combinator
          * adds a ''reason'' that the error occurred. This is in complement to the label. The `reason` is only added when
          * the parser fails, and will disappear if any further progress in the parser is made (unlike labels, which may
          * reappear as "hints").
          * @param reason The reason why a parser failed
          * @since 3.0.0
          */
        def explain(reason: String): Parsley[A] = new Parsley(new deepembedding.ErrorExplain(p.internal, reason))
        /** Hides the "expected" error message for a parser.
          * @since 3.0.0 */
        def hide: Parsley[A] = this.label("")
        /** Same as `fail`, except allows for a message generated from the result of the failed parser. In essence, this
          * is equivalent to `p >>= (x => fail(msggen(x))` but requires no expensive computations from the use of `>>=`.
          * @param msggen The generator function for error message, creating a message based on the result of invokee
          * @return A parser that fails if it succeeds, with the given generator used to produce the error message
          */
        def !(msggen: A => String): Parsley[Nothing] = new Parsley(new deepembedding.FastFail(p.internal, msggen))
        /** Same as `unexpected`, except allows for a message generated from the result of the failed parser. In essence,
          * this is equivalent to `p >>= (x => unexpected(x))` but requires no expensive computations from the use of
          * `>>=`
          * @param msggen The generator function for error message, creating a message based on the result of invokee
          * @return A parser that fails if it succeeds, with the given generator used to produce an unexpected message
          */
        def unexpected(msggen: A => String): Parsley[Nothing] = new Parsley(new deepembedding.FastUnexpected(p.internal, msggen))
    }
}