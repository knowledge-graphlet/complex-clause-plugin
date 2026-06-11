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

import org.eclipse.collections.api.list.ImmutableList;

import java.util.UUID;

/**
 * One node of a complex-concept clause: a typed view over a single {@code EntityVertex} of the
 * clause's {@code DiTree<EntityVertex>} source graph. This is the clause analogue of
 * {@code dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom}; the only implementor is
 * {@link ClauseAdaptor}, whose concrete subclasses expose operator-specific accessors.
 */
public sealed interface Clause permits ClauseAdaptor {

    /** @return this node's vertex index within the source graph. */
    int vertexIndex();

    /** @return this node's vertex UUID. */
    UUID vertexUUID();

    /** @return the operator kind of this node. */
    ClauseSemantic clauseSemantic();

    /** @return the child clauses in edge-insertion order (operand order is significant). */
    ImmutableList<Clause> children();
}
