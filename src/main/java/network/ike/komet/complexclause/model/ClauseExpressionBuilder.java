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

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.ConceptFacade;
import network.ike.komet.complexclause.terms.ComplexClauseTerms;

import java.util.UUID;

/**
 * Fluent builder for a complex-concept clause graph, mirroring
 * {@code dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder}: each factory makes an
 * {@code EntityVertex}, wires its operand edges, sets its literal/reference properties, and returns
 * the typed adaptor. Attach the top-level boolean expression with {@link #setExpression(Clause)}, then
 * {@link #build()} (or {@link #asDiTree()} to write the graph into a semantic field).
 */
public final class ClauseExpressionBuilder {

    private final DiTreeEntity.Builder builder;
    private ClauseExpression expression;
    private final int rootIndex;

    private static UUID randomUuid() {
        return UuidT5Generator.get(UuidT5Generator.STAMP_NAMESPACE,
                Thread.currentThread().threadId() + "-" + System.nanoTime());
    }

    public ClauseExpressionBuilder() {
        this.builder = DiTreeEntity.builder();
        this.expression = new ClauseExpression(builder);
        EntityVertex root = EntityVertex.make(randomUuid(), ClauseSemantic.ROOT.nid);
        builder.addVertex(root);
        builder.setRoot(root);
        this.rootIndex = root.vertexIndex();
        new ClauseAdaptor.RootAdaptor(expression, rootIndex);
    }

    /** @return the root adaptor. */
    public ClauseAdaptor.RootAdaptor root() {
        return (ClauseAdaptor.RootAdaptor) expression.adaptor(rootIndex);
    }

    /**
     * Wires the top-level boolean expression under the root.
     *
     * @param topExpression the clause's top boolean node
     * @return this builder
     */
    public ClauseExpressionBuilder setExpression(Clause topExpression) {
        builder.addEdge(topExpression.vertexIndex(), rootIndex);
        return this;
    }

    /** @return a built, immutable clause expression. */
    public ClauseExpression build() {
        return new ClauseExpression(builder.build());
    }

    /** @return the built clause graph, for writing into a semantic field. */
    public DiTreeEntity asDiTree() {
        return builder.build();
    }

    // ---- Connectives -----------------------------------------------------------------------------

    public ClauseAdaptor.NaryConnectiveAdaptor And(Clause... operands) {
        return nary(ClauseSemantic.AND, operands);
    }

    public ClauseAdaptor.NaryConnectiveAdaptor Or(Clause... operands) {
        return nary(ClauseSemantic.OR, operands);
    }

    private ClauseAdaptor.NaryConnectiveAdaptor nary(ClauseSemantic operator, Clause... operands) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), operator.nid);
        builder.addVertex(vertex);
        for (Clause operand : operands) {
            builder.addEdge(operand.vertexIndex(), vertex.vertexIndex());
        }
        return new ClauseAdaptor.NaryConnectiveAdaptor(expression, vertex.vertexIndex());
    }

    public ClauseAdaptor.NotAdaptor Not(Clause operand) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), ClauseSemantic.NOT.nid);
        builder.addVertex(vertex);
        builder.addEdge(operand.vertexIndex(), vertex.vertexIndex());
        return new ClauseAdaptor.NotAdaptor(expression, vertex.vertexIndex());
    }

    // ---- Comparisons -----------------------------------------------------------------------------

    public ClauseAdaptor.ComparisonAdaptor Equal(Clause left, Clause right) {
        return comparison(ClauseSemantic.EQUAL, left, right);
    }

    public ClauseAdaptor.ComparisonAdaptor Greater(Clause left, Clause right) {
        return comparison(ClauseSemantic.GREATER, left, right);
    }

    public ClauseAdaptor.ComparisonAdaptor GreaterOrEqual(Clause left, Clause right) {
        return comparison(ClauseSemantic.GREATER_OR_EQUAL, left, right);
    }

    public ClauseAdaptor.ComparisonAdaptor Less(Clause left, Clause right) {
        return comparison(ClauseSemantic.LESS, left, right);
    }

    public ClauseAdaptor.ComparisonAdaptor LessOrEqual(Clause left, Clause right) {
        return comparison(ClauseSemantic.LESS_OR_EQUAL, left, right);
    }

    private ClauseAdaptor.ComparisonAdaptor comparison(ClauseSemantic operator, Clause left, Clause right) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), operator.nid);
        builder.addVertex(vertex);
        builder.addEdge(left.vertexIndex(), vertex.vertexIndex());
        builder.addEdge(right.vertexIndex(), vertex.vertexIndex());
        return new ClauseAdaptor.ComparisonAdaptor(expression, vertex.vertexIndex());
    }

    // ---- Existence -------------------------------------------------------------------------------

    public ClauseAdaptor.ExistsAdaptor Exists(Clause source) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), ClauseSemantic.EXISTS.nid);
        builder.addVertex(vertex);
        builder.addEdge(source.vertexIndex(), vertex.vertexIndex());
        return new ClauseAdaptor.ExistsAdaptor(expression, vertex.vertexIndex());
    }

    public ClauseAdaptor.InAdaptor In(Clause subject, Clause valueSetRef) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), ClauseSemantic.IN.nid);
        builder.addVertex(vertex);
        builder.addEdge(subject.vertexIndex(), vertex.vertexIndex());
        builder.addEdge(valueSetRef.vertexIndex(), vertex.vertexIndex());
        return new ClauseAdaptor.InAdaptor(expression, vertex.vertexIndex());
    }

    // ---- Terminology -----------------------------------------------------------------------------

    public ClauseAdaptor.ValueSetRefAdaptor ValueSetRef(ConceptFacade layer1Class) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), ClauseSemantic.VALUE_SET_REF.nid);
        builder.addVertex(vertex);
        vertex.putUncommittedProperty(ComplexClauseTerms.OPERAND_CONCEPT.nid(), layer1Class);
        vertex.commitProperties();
        return new ClauseAdaptor.ValueSetRefAdaptor(expression, vertex.vertexIndex());
    }

    public ClauseAdaptor.RetrieveAdaptor Retrieve(ConceptFacade resourceType, ConceptFacade valueSetClass) {
        return Retrieve(resourceType, valueSetClass, "R", null);
    }

    public ClauseAdaptor.RetrieveAdaptor Retrieve(ConceptFacade resourceType, ConceptFacade valueSetClass,
                                                  String alias, Clause where) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), ClauseSemantic.RETRIEVE.nid);
        builder.addVertex(vertex);
        vertex.putUncommittedProperty(ComplexClauseTerms.RESOURCE_TYPE.nid(), resourceType);
        if (valueSetClass != null) {
            vertex.putUncommittedProperty(ComplexClauseTerms.OPERAND_CONCEPT.nid(), valueSetClass);
        }
        if (alias != null) {
            vertex.putUncommittedProperty(ComplexClauseTerms.RETRIEVE_PATH.nid(), alias);
        }
        vertex.commitProperties();
        if (where != null) {
            builder.addEdge(where.vertexIndex(), vertex.vertexIndex());
        }
        return new ClauseAdaptor.RetrieveAdaptor(expression, vertex.vertexIndex());
    }

    /**
     * Attaches a {@code where} filter to a retrieve after construction (so the filter may reference
     * the retrieve's alias).
     *
     * @param retrieve the retrieve
     * @param where    the boolean filter expression
     * @return this builder
     */
    public ClauseExpressionBuilder where(ClauseAdaptor.RetrieveAdaptor retrieve, Clause where) {
        builder.addEdge(where.vertexIndex(), retrieve.vertexIndex());
        return this;
    }

    // ---- Navigation ------------------------------------------------------------------------------

    /** A property path on a named source, e.g. {@code "BMI determination".value}. */
    public ClauseAdaptor.PropertyAdaptor Property(String sourceLabel, String path) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), ClauseSemantic.PROPERTY.nid);
        builder.addVertex(vertex);
        if (sourceLabel != null) {
            vertex.putUncommittedProperty(ComplexClauseTerms.SOURCE_LABEL.nid(), sourceLabel);
        }
        vertex.putUncommittedProperty(ComplexClauseTerms.RETRIEVE_PATH.nid(), path);
        vertex.commitProperties();
        return new ClauseAdaptor.PropertyAdaptor(expression, vertex.vertexIndex());
    }

    /** A property path on a source expression (e.g. a retrieve alias). */
    public ClauseAdaptor.PropertyAdaptor Property(Clause source, String path) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), ClauseSemantic.PROPERTY.nid);
        builder.addVertex(vertex);
        vertex.putUncommittedProperty(ComplexClauseTerms.RETRIEVE_PATH.nid(), path);
        vertex.commitProperties();
        builder.addEdge(source.vertexIndex(), vertex.vertexIndex());
        return new ClauseAdaptor.PropertyAdaptor(expression, vertex.vertexIndex());
    }

    // ---- Aggregate -------------------------------------------------------------------------------

    public ClauseAdaptor.AggregateAdaptor Count(Clause source) {
        return aggregate(ClauseSemantic.COUNT, source);
    }

    public ClauseAdaptor.AggregateAdaptor Sum(Clause source) {
        return aggregate(ClauseSemantic.SUM, source);
    }

    private ClauseAdaptor.AggregateAdaptor aggregate(ClauseSemantic operator, Clause source) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), operator.nid);
        builder.addVertex(vertex);
        builder.addEdge(source.vertexIndex(), vertex.vertexIndex());
        return new ClauseAdaptor.AggregateAdaptor(expression, vertex.vertexIndex());
    }

    // ---- Temporal / interval ---------------------------------------------------------------------

    public ClauseAdaptor.IntervalOpAdaptor IntervalIn(Clause left, Clause right) {
        return intervalOp(ClauseSemantic.INTERVAL_IN, left, right);
    }

    public ClauseAdaptor.IntervalOpAdaptor IncludedIn(Clause left, Clause right) {
        return intervalOp(ClauseSemantic.INCLUDED_IN, left, right);
    }

    public ClauseAdaptor.IntervalOpAdaptor Overlaps(Clause left, Clause right) {
        return intervalOp(ClauseSemantic.OVERLAPS, left, right);
    }

    private ClauseAdaptor.IntervalOpAdaptor intervalOp(ClauseSemantic operator, Clause left, Clause right) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), operator.nid);
        builder.addVertex(vertex);
        builder.addEdge(left.vertexIndex(), vertex.vertexIndex());
        builder.addEdge(right.vertexIndex(), vertex.vertexIndex());
        return new ClauseAdaptor.IntervalOpAdaptor(expression, vertex.vertexIndex());
    }

    // ---- Literals --------------------------------------------------------------------------------

    public ClauseAdaptor.LiteralAdaptor Quantity(double value, String unit) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), ClauseSemantic.QUANTITY.nid);
        builder.addVertex(vertex);
        vertex.putUncommittedProperty(ComplexClauseTerms.OPERAND_VALUE.nid(), (float) value);
        if (unit != null) {
            vertex.putUncommittedProperty(ComplexClauseTerms.OPERAND_UNIT.nid(), unit);
        }
        vertex.commitProperties();
        return new ClauseAdaptor.LiteralAdaptor(expression, vertex.vertexIndex());
    }

    public ClauseAdaptor.LiteralAdaptor IntegerLiteral(int value) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), ClauseSemantic.INTEGER.nid);
        builder.addVertex(vertex);
        vertex.putUncommittedProperty(ComplexClauseTerms.OPERAND_VALUE.nid(), value);
        vertex.commitProperties();
        return new ClauseAdaptor.LiteralAdaptor(expression, vertex.vertexIndex());
    }

    public ClauseAdaptor.LiteralAdaptor BooleanLiteral(boolean value) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), ClauseSemantic.BOOLEAN.nid);
        builder.addVertex(vertex);
        vertex.putUncommittedProperty(ComplexClauseTerms.OPERAND_VALUE.nid(), value);
        vertex.commitProperties();
        return new ClauseAdaptor.LiteralAdaptor(expression, vertex.vertexIndex());
    }

    public ClauseAdaptor.LiteralAdaptor Code(ConceptFacade code) {
        EntityVertex vertex = EntityVertex.make(randomUuid(), ClauseSemantic.CODE.nid);
        builder.addVertex(vertex);
        vertex.putUncommittedProperty(ComplexClauseTerms.OPERAND_CONCEPT.nid(), code);
        vertex.commitProperties();
        return new ClauseAdaptor.LiteralAdaptor(expression, vertex.vertexIndex());
    }
}
