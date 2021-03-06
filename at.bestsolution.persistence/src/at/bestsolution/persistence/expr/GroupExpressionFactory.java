/*******************************************************************************
 * Copyright (c) 2014 BestSolution.at and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tom Schindl <tom.schindl@bestsolution.at> - initial API and implementation
 *******************************************************************************/
package at.bestsolution.persistence.expr;

import java.util.Arrays;
import java.util.Collection;

public final class GroupExpressionFactory {
	GroupExpressionFactory() {
	}

	@SuppressWarnings("unchecked")
	public static <O> AndExpression<O> and(Expression<O> x1, Expression<O> x2) {
		return and(Arrays.asList(x1,x2));
	}

	@SuppressWarnings("unchecked")
	public static <O> AndExpression<O> and(Expression<O> x1, Expression<O> x2, Expression<O> x3) {
		return and(Arrays.asList(x1,x2,x3));
	}

	@SuppressWarnings("unchecked")
	public static <O> AndExpression<O> and(Expression<O> x1, Expression<O> x2, Expression<O> x3, Expression<O> x4) {
		return and(Arrays.asList(x1,x2,x3,x4));
	}

	@SuppressWarnings("unchecked")
	public static <O> AndExpression<O> and(Expression<O> x1, Expression<O> x2, Expression<O> x3, Expression<O> x4, Expression<O> x5) {
		return and(Arrays.asList(x1,x2,x3,x4,x5));
	}

	@SuppressWarnings("unchecked")
	public static <O> AndExpression<O> and(Expression<O> x1, Expression<O> x2, Expression<O> x3, Expression<O> x4, Expression<O> x5, Expression<O> x6) {
		return and(Arrays.asList(x1,x2,x3,x4,x5,x6));
	}

	@SuppressWarnings("unchecked")
	public static <O> AndExpression<O> and(Expression<O> x1, Expression<O> x2, Expression<O> x3, Expression<O> x4, Expression<O> x5, Expression<O> x6, Expression<O> x7) {
		return and(Arrays.asList(x1,x2,x3,x4,x5,x6,x7));
	}

	public static <O> AndExpression<O> and(Collection<Expression<O>> expressions) {
		return new AndExpression<O>(expressions);
	}

	public static <O> OrExpresion<O> or(Collection<Expression<O>> expressions) {
		return new OrExpresion<O>(expressions);
	}

	@SuppressWarnings("unchecked")
	public static <O> OrExpresion<O> or(Expression<O> x1, Expression<O> x2) {
		return or(Arrays.asList(x1,x2));
	}

	@SuppressWarnings("unchecked")
	public static <O> OrExpresion<O> or(Expression<O> x1, Expression<O> x2, Expression<O> x3) {
		return or(Arrays.asList(x1,x2,x3));
	}

	@SuppressWarnings("unchecked")
	public static <O> OrExpresion<O> or(Expression<O> x1, Expression<O> x2, Expression<O> x3, Expression<O> x4) {
		return or(Arrays.asList(x1,x2,x3,x4));
	}

	@SuppressWarnings("unchecked")
	public static <O> OrExpresion<O> or(Expression<O> x1, Expression<O> x2, Expression<O> x3, Expression<O> x4, Expression<O> x5) {
		return or(Arrays.asList(x1,x2,x3,x4,x5));
	}

	@SuppressWarnings("unchecked")
	public static <O> OrExpresion<O> or(Expression<O> x1, Expression<O> x2, Expression<O> x3, Expression<O> x4, Expression<O> x5, Expression<O> x6) {
		return or(Arrays.asList(x1,x2,x3,x4,x5,x6));
	}

	@SuppressWarnings("unchecked")
	public static <O> OrExpresion<O> or(Expression<O> x1, Expression<O> x2, Expression<O> x3, Expression<O> x4, Expression<O> x5, Expression<O> x6, Expression<O> x7) {
		return or(Arrays.asList(x1,x2,x3,x4,x5,x6,x7));
	}
}
