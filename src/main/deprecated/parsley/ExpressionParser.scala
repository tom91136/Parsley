package parsley

import parsley.ExpressionParser.Levels
import parsley.XCompat._

// $COVERAGE-OFF$
/** This class is used to generate efficient expression parsers given a table of operators
  * in operator of operator precedence and an atomic value that represents the smallest
  * part of the expression. Caters to unary and binary operators of different associativities.
  */
@deprecated("This class will be removed in Parsley 3.0, use `parsley.expr.precedence` instead", "v2.2.0")
final class ExpressionParser[-A, +B] private (atom: =>Parsley[A], table: Levels[A, B])
{
    /** The expression parser generated by this generator. */
    lazy val expr: Parsley[B] = parsley.expr.precedence(atom, table)
}

@deprecated("This object will be removed in Parsley 3.0, use `parsley.expr.precedence` instead", "v2.2.0")
object ExpressionParser
{
    /** This is used to build an expression parser for a monolithic type. Levels are specified from strongest
     * to weakest.
     * @tparam A The type of the monolithic tree
     * @param atom The atomic unit of the expression, for instance numbers/variables
     * @param table A table of operators. Table is ordered highest precedence to lowest precedence.
     *              Each list in the table corresponds to operators of the same precedence level.
     * @return A parser for the described expression language
     */
    @deprecated("This class will be removed in Parsley 3.0, use `parsley.expr.precedence` instead", "v2.2.0")
    def apply[A](atom: =>Parsley[A], table: MonoOps[A]*): ExpressionParser[A, A] = {
        new ExpressionParser(atom, table.foldRight(Levels.empty[A])(Level.apply[A, A, A]))
    }
    /** This is used to build an expression parser for a multi-layered expression tree type. Levels are specified
     * from strongest to weakest.
     * @tparam A The type of the atomic unit of the expression
     * @tparam B The type of the resulting parse tree (outermost operations)
     * @param atom The atomic unit of the expression
     * @param table A table of operators. Table is ordered highest precedence to lowest precedence.
     *              See [[Levels]] and it's subtypes for a description of how the types work.
     * @return A parser for the described expression language
     */
    @deprecated("This class will be removed in Parsley 3.0, use `parsley.expr.precedence` instead", "v2.2.0")
    def apply[A, B](atom: =>Parsley[A], table: Levels[A, B]): ExpressionParser[A, B] = new ExpressionParser(atom, table)

    /** Denotes the associativity of an operator, either `AssocLeft` or `AssocRight`. */
    @deprecated("This class will be removed in Parsley 3.0, see [[Infixes]] for how this is replaced in new-style", "v2.2.0")
    sealed trait Assoc
    @deprecated("This class will be removed in Parsley 3.0, see [[Infixes]] for how this is replaced in new-style", "v2.2.0")
    case object AssocLeft extends Assoc
    @deprecated("This class will be removed in Parsley 3.0, see [[Infixes]] for how this is replaced in new-style", "v2.2.0")
    case object AssocRight extends Assoc

    /** Denotes the fixity of an operator, either `Prefix` or `Postfix`. */
    @deprecated("This class will be removed in Parsley 3.0, see [[Unaries]] for how this is replaced in new-style", "v2.2.0")
    sealed trait Fixity
    @deprecated("This class will be removed in Parsley 3.0, see [[Unaries]] for how this is replaced in new-style", "v2.2.0")
    case object Prefix extends Fixity
    @deprecated("This class will be removed in Parsley 3.0, see [[Unaries]] for how this is replaced in new-style", "v2.2.0")
    case object Postfix extends Fixity

    type MonoOps[A] = Ops[A, A]
    /** A list of operators on the same precedence level. Note operators of different fixities cannot
      * mix on the same level of indentation. Either `Lefts` which is a list of infix left-assocative
      * operators, `Rights` which is a list of infix right-associative operators, `Prefixes` which is
      * a list of prefixing unary operators or `Postfixes` a list of postfixing unary operators.
      *
      * Each list of operators will also require a wrapping function of type `A => B`. This allows it
      * to convert values of type `A` into values of type `B` when there is no more operation to perform.
      * @tparam A The type of the layer below
      * @tparam B The type of this layer
      */
    @deprecated("This type will be removed in Parsley 3.0, use `parsley.expr.Ops` instead", "v2.2.0")
    type Ops[-A, B] = expr.Ops[A, B]
    @deprecated("This will be removed in Parsley 3.0, use `parsley.expr.GOps` instead", "v2.2.0")
    object Lefts {
        def apply[A, B](ops: Parsley[(B, A) => B]*)(implicit wrap: A => B): Ops[A, B] = expr.GOps[A, B](expr.InfixL)(ops: _*)
    }
    @deprecated("This will be removed in Parsley 3.0, use `parsley.expr.GOps` instead", "v2.2.0")
    object Rights {
        def apply[A, B](ops: Parsley[(A, B) => B]*)(implicit wrap: A => B): Ops[A, B] = expr.GOps[A, B](expr.InfixR)(ops: _*)
    }
    @deprecated("This will be removed in Parsley 3.0, use `parsley.expr.GOps` instead", "v2.2.0")
    object Prefixes {
        def apply[A, B](ops: Parsley[B => B]*)(implicit wrap: A => B): Ops[A, B] = expr.GOps[A, B](expr.Prefix)(ops: _*)
    }
    @deprecated("This will be removed in Parsley 3.0, use `parsley.expr.GOps` instead", "v2.2.0")
    object Postfixes {
        def apply[A, B](ops: Parsley[B => B]*)(implicit wrap: A => B): Ops[A, B] = expr.GOps[A, B](expr.Postfix)(ops: _*)
    }

    object Infixes
    {
        /**
         * This is used to more succinctly describe binary precedence levels for monolithic types
         * (where all levels result in the same type). It represents either left or right associative
         * binary operations by providing an associativity.
         * @param assoc The associativity of the operation
         * @param ops The operators present on this level
         */
        @deprecated("This will be removed in Parsley 3.0, use `parsley.expr.Ops` instead with `parsley.expr.InfixL` or `parsley.expr.InfixR`", "v2.2.0")
        def apply[A](assoc: Assoc, ops: Parsley[(A, A) => A]*): MonoOps[A] = assoc match
        {
            case AssocLeft => expr.Ops(expr.InfixL)(ops: _*)
            case AssocRight => expr.Ops(expr.InfixR)(ops: _*)
        }
    }

    object Unaries
    {
        /**
         * This is used to more succinctly describe unary precedence levels for monolithic types
         * (where all levels result in the same type). It represents either prefix or postfix
         * unary operations by providing a fixity.
         * @param fixity The fixity of the operation
         * @param ops The operators present on this level
         */
        @deprecated("This will be removed in Parsley 3.0, use `parsley.expr.Ops` instead with `parsley.expr.Prefix` or `parsley.expr.Postfix`", "v2.2.0")
        def apply[A](fixity: Fixity, ops: Parsley[A => A]*): MonoOps[A] = fixity match
        {
            case Prefix => expr.Ops(expr.Prefix)(ops: _*)
            case Postfix => expr.Ops(expr.Postfix)(ops: _*)
        }
    }

    /**
     * For more complex expression parser types `Levels` can be used to
     * describe the precedence table whilst preserving the intermediate
     * structure between each level.
     * @tparam A The base type accepted by this list of levels
     * @tparam B The type of structure produced by the list of levels
     */
     @deprecated("This type will be removed in Parsley 3.0, use `parsley.expr.Levels` instead", "v2.2.0")
    type Levels[-A, +B] = expr.Levels[A, B]
    /**
     * This represents a single new level of the hierarchy, with stronger
     * precedence than its tail.
     * @tparam A The base type accepted by this layer
     * @tparam B The intermediate type that will be provided to the next layer
     * @tparam C The type of structure produced by the next layers
     * @param ops The operators accepted at this level
     * @param lvls The next, weaker, levels in the precedence table
     * @return A larger precedence table transforming atoms of type `A` into
     *          a structure of type `C`.
     */
    @deprecated("This class will be removed in Parsley 3.0, use `parsley.expr.Level` instead", "v2.2.0")
    object Level {
        def apply[A, B, C](ops: Ops[A, B], lvls: Levels[B, C]): Levels[A, C] = expr.Level(ops, lvls)
    }
    object Levels
    {
        /**
         * This represents the end of a precedence table. It will not
         * touch the structure in any way.
         * @tparam A The type of the structure to be produced by the table.
         */
        @deprecated("This method will be removed in Parsley 3.0, use `parsley.expr.Levels.empty` instead", "v2.2.0")
        def empty[A]: Levels[A, A] = expr.Levels.empty[A]
    }

    @deprecated("This class will be removed in Parsley 3.0, use `parsley.expr.Levels.LevelBuilder` instead", "v2.2.0")
    implicit class LevelBuilder[B, +C](lvls: Levels[B, C])
    {
        def +:[A](lvl: Ops[A, B]): Levels[A, C] = Level(lvl, lvls)
    }
}
// $COVERAGE-ON$