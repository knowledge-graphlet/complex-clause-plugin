/*
 * Copyright © 2026 Knowledge Graphlet / IKE Network
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package network.ike.komet.complexclause.model;

import dev.ikm.tinkar.terms.ConceptFacade;
import network.ike.komet.complexclause.terms.ComplexClauseTerms;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;

import java.util.UUID;

/**
 * Base for every clause node, mirroring
 * {@code dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiomAdaptor}: it wraps a vertex index
 * within an enclosing {@link ClauseExpression}, registers itself into the expression's adaptor list
 * on construction, and exposes the {@link #children()} / {@link #property(ConceptFacade)} helpers the
 * concrete operator adaptors read from.
 */
public abstract sealed class ClauseAdaptor implements Clause
        permits ClauseAdaptor.RootAdaptor, ClauseAdaptor.NaryConnectiveAdaptor, ClauseAdaptor.NotAdaptor,
        ClauseAdaptor.ComparisonAdaptor, ClauseAdaptor.ExistsAdaptor, ClauseAdaptor.InAdaptor,
        ClauseAdaptor.ValueSetRefAdaptor, ClauseAdaptor.RetrieveAdaptor, ClauseAdaptor.PropertyAdaptor,
        ClauseAdaptor.AggregateAdaptor, ClauseAdaptor.IntervalOpAdaptor, ClauseAdaptor.LiteralAdaptor {

    final ClauseExpression adaptedExpression;
    final int vertexIndex;

    ClauseAdaptor(ClauseExpression adaptedExpression, int vertexIndex) {
        this.adaptedExpression = adaptedExpression;
        this.vertexIndex = vertexIndex;
        MutableList<ClauseAdaptor> adaptors = adaptedExpression.mutableAdaptors();
        if (vertexIndex > -1 && vertexIndex < adaptors.size()) {
            adaptors.set(vertexIndex, this);
        } else if (vertexIndex == adaptors.size()) {
            adaptors.add(this);
        } else {
            while (vertexIndex > adaptors.size()) {
                adaptors.add(null);
            }
            adaptors.add(this);
        }
    }

    @Override
    public int vertexIndex() {
        return vertexIndex;
    }

    @Override
    public UUID vertexUUID() {
        return adaptedExpression.sourceGraph().vertex(vertexIndex).asUuid();
    }

    @Override
    public ClauseSemantic clauseSemantic() {
        return ClauseSemantic.get(adaptedExpression.sourceGraph().vertex(vertexIndex).getMeaningNid());
    }

    @Override
    public ImmutableList<Clause> children() {
        ImmutableIntList successors = adaptedExpression.sourceGraph().successors(vertexIndex);
        MutableList<Clause> result = Lists.mutable.ofInitialCapacity(successors.size());
        for (int childIndex : successors.toArray()) {
            result.add(adaptedExpression.adaptor(childIndex));
        }
        return result.toImmutable();
    }

    /**
     * Reads a typed vertex property by key.
     *
     * @param key the property-key concept
     * @param <O> the stored value type
     * @return the stored value, or {@code null} if absent
     */
    protected <O> O property(ConceptFacade key) {
        return adaptedExpression.sourceGraph().vertex(vertexIndex).propertyFast(key);
    }

    protected Clause onlyChild() {
        ImmutableList<Clause> children = children();
        if (children.size() != 1) {
            throw new IllegalStateException(clauseSemantic() + " expects exactly one child, found " + children.size());
        }
        return children.get(0);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + clauseSemantic() + "]";
    }

    /** Root of the clause graph; its single child is the top-level boolean expression. */
    public static final class RootAdaptor extends ClauseAdaptor {
        public RootAdaptor(ClauseExpression expression, int vertexIndex) {
            super(expression, vertexIndex);
        }

        /** @return the top-level boolean expression, or {@code null} if the clause is empty. */
        public Clause expression() {
            ImmutableList<Clause> children = children();
            return children.isEmpty() ? null : children.get(0);
        }
    }

    /** And / Or over an ordered list of operands. */
    public static final class NaryConnectiveAdaptor extends ClauseAdaptor {
        public NaryConnectiveAdaptor(ClauseExpression expression, int vertexIndex) {
            super(expression, vertexIndex);
        }

        public ImmutableList<Clause> elements() {
            return children();
        }
    }

    /** Logical negation of a single operand. */
    public static final class NotAdaptor extends ClauseAdaptor {
        public NotAdaptor(ClauseExpression expression, int vertexIndex) {
            super(expression, vertexIndex);
        }

        public Clause operand() {
            return onlyChild();
        }
    }

    /** A binary comparison ({@code =, >, >=, <, <=}); operands are ordered left then right. */
    public static final class ComparisonAdaptor extends ClauseAdaptor {
        public ComparisonAdaptor(ClauseExpression expression, int vertexIndex) {
            super(expression, vertexIndex);
        }

        public Clause left() {
            return children().get(0);
        }

        public Clause right() {
            return children().get(1);
        }
    }

    /** Existential test over a single source expression (typically a {@link RetrieveAdaptor}). */
    public static final class ExistsAdaptor extends ClauseAdaptor {
        public ExistsAdaptor(ClauseExpression expression, int vertexIndex) {
            super(expression, vertexIndex);
        }

        public Clause source() {
            return onlyChild();
        }
    }

    /** Value-set membership: subject {@code in} a value set reference. */
    public static final class InAdaptor extends ClauseAdaptor {
        public InAdaptor(ClauseExpression expression, int vertexIndex) {
            super(expression, vertexIndex);
        }

        public Clause subject() {
            return children().get(0);
        }

        public Clause valueSet() {
            return children().get(1);
        }
    }

    /** Reference to a Layer-1 value set: the EL++ class whose inferred subsumption set is the set. */
    public static final class ValueSetRefAdaptor extends ClauseAdaptor {
        public ValueSetRefAdaptor(ClauseExpression expression, int vertexIndex) {
            super(expression, vertexIndex);
        }

        public ConceptFacade valueSetConcept() {
            return property(ComplexClauseTerms.OPERAND_CONCEPT);
        }
    }

    /** A data retrieve ({@code [ResourceType: "ValueSet"] Alias where ...}). */
    public static final class RetrieveAdaptor extends ClauseAdaptor {
        public RetrieveAdaptor(ClauseExpression expression, int vertexIndex) {
            super(expression, vertexIndex);
        }

        public ConceptFacade resourceType() {
            return property(ComplexClauseTerms.RESOURCE_TYPE);
        }

        /** @return the Layer-1 value-set class to filter by, or {@code null}. */
        public ConceptFacade valueSetConcept() {
            return property(ComplexClauseTerms.OPERAND_CONCEPT);
        }

        /** @return the query alias (defaults to {@code R}). */
        public String alias() {
            String alias = property(ComplexClauseTerms.RETRIEVE_PATH);
            return alias == null ? "R" : alias;
        }

        /** @return the optional {@code where} filter expression, or {@code null}. */
        public Clause where() {
            ImmutableList<Clause> children = children();
            return children.isEmpty() ? null : children.get(0);
        }
    }

    /** Navigation into a value: {@code "<sourceLabel>".path} or {@code <source>.path}. */
    public static final class PropertyAdaptor extends ClauseAdaptor {
        public PropertyAdaptor(ClauseExpression expression, int vertexIndex) {
            super(expression, vertexIndex);
        }

        public String path() {
            return property(ComplexClauseTerms.RETRIEVE_PATH);
        }

        /** @return a named source label (e.g. a CQL define name), or {@code null}. */
        public String sourceLabel() {
            return property(ComplexClauseTerms.SOURCE_LABEL);
        }

        /** @return the source expression navigated, or {@code null} for a bare/aliased path. */
        public Clause source() {
            ImmutableList<Clause> children = children();
            return children.isEmpty() ? null : children.get(0);
        }
    }

    /** An aggregate ({@code Count}, {@code Sum}) over a single source expression. */
    public static final class AggregateAdaptor extends ClauseAdaptor {
        public AggregateAdaptor(ClauseExpression expression, int vertexIndex) {
            super(expression, vertexIndex);
        }

        public Clause source() {
            return onlyChild();
        }
    }

    /** An interval/temporal operator ({@code in}, {@code included in}, {@code overlaps}). */
    public static final class IntervalOpAdaptor extends ClauseAdaptor {
        public IntervalOpAdaptor(ClauseExpression expression, int vertexIndex) {
            super(expression, vertexIndex);
        }

        public Clause left() {
            return children().get(0);
        }

        public Clause right() {
            return children().get(1);
        }
    }

    /** A literal: Quantity (value + unit), Integer, Boolean, or Code (a concept reference). */
    public static final class LiteralAdaptor extends ClauseAdaptor {
        public LiteralAdaptor(ClauseExpression expression, int vertexIndex) {
            super(expression, vertexIndex);
        }

        /** @return the scalar value (Float/Integer/Boolean), or {@code null} for a Code literal. */
        public Object value() {
            return property(ComplexClauseTerms.OPERAND_VALUE);
        }

        /** @return the UCUM unit for a Quantity literal, or {@code null}. */
        public String unit() {
            return property(ComplexClauseTerms.OPERAND_UNIT);
        }

        /** @return the referenced concept for a Code literal, or {@code null}. */
        public ConceptFacade code() {
            return property(ComplexClauseTerms.OPERAND_CONCEPT);
        }
    }
}
