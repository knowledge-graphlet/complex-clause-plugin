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

import dev.ikm.tinkar.component.graph.DiTree;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;

/**
 * A complex-concept clause: a {@code DiTree<EntityVertex>} source graph plus one {@link ClauseAdaptor}
 * per vertex, mirroring {@code dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression}. The
 * constructor instantiates the correct adaptor for each vertex by switching on
 * {@link ClauseSemantic#get(int)} of the vertex meaning; each adaptor self-registers into
 * {@link #mutableAdaptors()}.
 */
public final class ClauseExpression {

    private final DiTree<EntityVertex> sourceGraph;
    private final MutableList<ClauseAdaptor> adaptors;

    /**
     * Builds the adaptor view over a clause source graph (a built {@link DiTreeEntity} or a
     * {@link DiTreeEntity.Builder}).
     *
     * @param sourceGraph the clause graph
     */
    public ClauseExpression(DiTree<EntityVertex> sourceGraph) {
        this.sourceGraph = sourceGraph;
        int vertexCount = sourceGraph.vertexMap().size();
        this.adaptors = Lists.mutable.ofInitialCapacity(vertexCount);
        for (int i = 0; i < vertexCount; i++) {
            EntityVertex vertex = sourceGraph.vertex(i);
            if (vertex == null) {
                adaptors.add(null);
                continue;
            }
            switch (ClauseSemantic.get(vertex.getMeaningNid())) {
                case ROOT -> new ClauseAdaptor.RootAdaptor(this, i);
                case AND, OR -> new ClauseAdaptor.NaryConnectiveAdaptor(this, i);
                case NOT -> new ClauseAdaptor.NotAdaptor(this, i);
                case EQUAL, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL ->
                        new ClauseAdaptor.ComparisonAdaptor(this, i);
                case EXISTS -> new ClauseAdaptor.ExistsAdaptor(this, i);
                case IN -> new ClauseAdaptor.InAdaptor(this, i);
                case VALUE_SET_REF -> new ClauseAdaptor.ValueSetRefAdaptor(this, i);
                case RETRIEVE -> new ClauseAdaptor.RetrieveAdaptor(this, i);
                case PROPERTY -> new ClauseAdaptor.PropertyAdaptor(this, i);
                case COUNT, SUM -> new ClauseAdaptor.AggregateAdaptor(this, i);
                case INTERVAL_IN, INCLUDED_IN, OVERLAPS -> new ClauseAdaptor.IntervalOpAdaptor(this, i);
                case QUANTITY, INTEGER, BOOLEAN, CODE -> new ClauseAdaptor.LiteralAdaptor(this, i);
            }
        }
    }

    /** Package-private: the mutable adaptor list adaptors register themselves into. */
    MutableList<ClauseAdaptor> mutableAdaptors() {
        return adaptors;
    }

    /** Package-private: the adaptor at a vertex index. */
    Clause adaptor(int vertexIndex) {
        return adaptors.get(vertexIndex);
    }

    /** @return the underlying clause source graph. */
    public DiTree<EntityVertex> sourceGraph() {
        return sourceGraph;
    }

    /** @return the root adaptor. */
    public ClauseAdaptor.RootAdaptor root() {
        return (ClauseAdaptor.RootAdaptor) adaptors.get(sourceGraph.root().vertexIndex());
    }

    /** @return the top-level boolean expression (the root's single child), or {@code null}. */
    public Clause expression() {
        return root().expression();
    }

    /**
     * Wraps a built clause graph as an expression.
     *
     * @param diTree the built clause graph
     * @return the adaptor view
     */
    public static ClauseExpression from(DiTreeEntity diTree) {
        return new ClauseExpression(diTree);
    }
}
