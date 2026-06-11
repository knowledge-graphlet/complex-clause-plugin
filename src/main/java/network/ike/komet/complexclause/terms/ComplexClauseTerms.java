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
package network.ike.komet.complexclause.terms;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.terms.EntityProxy;

import java.util.UUID;

/**
 * Stable terminology for the complex-clause (Layer-2 / computable-logic) vocabulary: the pattern,
 * the field meaning/purpose, the HL7-ELM-mirroring operator concepts, and the vertex property keys.
 *
 * <p>Every identifier is a type-5 UUID derived over a fixed {@link #NAMESPACE}, so a clause authored
 * on one machine resolves to the same concepts on another (the IKE Syncthing/Git workspace spans two
 * machines) and the vocabulary is a clean lift-and-shift if it is later promoted into tinkar-core.
 */
public final class ComplexClauseTerms {

    private ComplexClauseTerms() {
    }

    /** Fixed type-5 namespace for all complex-clause identifiers. */
    public static final UUID NAMESPACE = UUID.fromString("b9c8a7d6-e5f4-4a3b-8c2d-1e0f9a8b7c6d");

    private static EntityProxy.Concept concept(String name, String key) {
        return EntityProxy.Concept.make(name, UuidT5Generator.get(NAMESPACE, "network.ike.komet.complexclause:" + key));
    }

    private static EntityProxy.Pattern pattern(String name, String key) {
        return EntityProxy.Pattern.make(name, UuidT5Generator.get(NAMESPACE, "network.ike.komet.complexclause:" + key));
    }

    // ---- Pattern + field meaning/purpose ---------------------------------------------------------

    /** Purpose of the clause facet: computable logic that EL++ cannot carry. */
    public static final EntityProxy.Concept COMPUTABLE_LOGIC =
            concept("Computable logic (complex clause)", "COMPUTABLE_LOGIC");
    /** Meaning of the pattern: a complex concept clause. */
    public static final EntityProxy.Concept COMPLEX_CONCEPT_CLAUSE =
            concept("Complex concept clause", "COMPLEX_CONCEPT_CLAUSE");
    /** Meaning of the single DiTree field: the clause definition graph. */
    public static final EntityProxy.Concept CLAUSE_DEFINITION_GRAPH =
            concept("Complex concept clause definition graph", "CLAUSE_DEFINITION_GRAPH");
    /**
     * The complex-concept-clause pattern. One {@code DITREE} field (the Layer-2 expression tree),
     * referenced component = the Complex Concept it constrains. Sits alongside the concept's EL++
     * stated/inferred axiom semantics — "one concept, two patterns".
     */
    public static final EntityProxy.Pattern COMPLEX_CONCEPT_CLAUSE_PATTERN =
            pattern("Complex Concept Clause Pattern", "COMPLEX_CONCEPT_CLAUSE_PATTERN");

    // ---- Clause graph root -----------------------------------------------------------------------

    public static final EntityProxy.Concept CLAUSE_DEFINITION_ROOT =
            concept("Clause definition root", "CLAUSE_DEFINITION_ROOT");

    // ---- Connectives -----------------------------------------------------------------------------

    public static final EntityProxy.Concept CLAUSE_AND = concept("Clause And", "CLAUSE_AND");
    public static final EntityProxy.Concept CLAUSE_OR = concept("Clause Or", "CLAUSE_OR");
    public static final EntityProxy.Concept CLAUSE_NOT = concept("Clause Not", "CLAUSE_NOT");

    // ---- Comparisons -----------------------------------------------------------------------------

    public static final EntityProxy.Concept CLAUSE_EQUAL = concept("Clause Equal", "CLAUSE_EQUAL");
    public static final EntityProxy.Concept CLAUSE_GREATER = concept("Clause Greater", "CLAUSE_GREATER");
    public static final EntityProxy.Concept CLAUSE_GREATER_OR_EQUAL =
            concept("Clause Greater or equal", "CLAUSE_GREATER_OR_EQUAL");
    public static final EntityProxy.Concept CLAUSE_LESS = concept("Clause Less", "CLAUSE_LESS");
    public static final EntityProxy.Concept CLAUSE_LESS_OR_EQUAL =
            concept("Clause Less or equal", "CLAUSE_LESS_OR_EQUAL");

    // ---- Existence -------------------------------------------------------------------------------

    public static final EntityProxy.Concept CLAUSE_EXISTS = concept("Clause Exists", "CLAUSE_EXISTS");
    public static final EntityProxy.Concept CLAUSE_IN = concept("Clause In (value set)", "CLAUSE_IN");

    // ---- Terminology -----------------------------------------------------------------------------

    public static final EntityProxy.Concept CLAUSE_VALUE_SET_REF =
            concept("Clause Value set reference", "CLAUSE_VALUE_SET_REF");
    public static final EntityProxy.Concept CLAUSE_RETRIEVE = concept("Clause Retrieve", "CLAUSE_RETRIEVE");

    // ---- Navigation ------------------------------------------------------------------------------

    public static final EntityProxy.Concept CLAUSE_PROPERTY = concept("Clause Property (path)", "CLAUSE_PROPERTY");

    // ---- Aggregate -------------------------------------------------------------------------------

    public static final EntityProxy.Concept CLAUSE_COUNT = concept("Clause Count", "CLAUSE_COUNT");
    public static final EntityProxy.Concept CLAUSE_SUM = concept("Clause Sum", "CLAUSE_SUM");

    // ---- Temporal / interval ---------------------------------------------------------------------

    public static final EntityProxy.Concept CLAUSE_INTERVAL_IN = concept("Clause In (interval)", "CLAUSE_INTERVAL_IN");
    public static final EntityProxy.Concept CLAUSE_INCLUDED_IN = concept("Clause Included in", "CLAUSE_INCLUDED_IN");
    public static final EntityProxy.Concept CLAUSE_OVERLAPS = concept("Clause Overlaps", "CLAUSE_OVERLAPS");

    // ---- Literals --------------------------------------------------------------------------------

    public static final EntityProxy.Concept CLAUSE_QUANTITY = concept("Clause Quantity literal", "CLAUSE_QUANTITY");
    public static final EntityProxy.Concept CLAUSE_INTEGER = concept("Clause Integer literal", "CLAUSE_INTEGER");
    public static final EntityProxy.Concept CLAUSE_BOOLEAN = concept("Clause Boolean literal", "CLAUSE_BOOLEAN");
    public static final EntityProxy.Concept CLAUSE_CODE = concept("Clause Code literal", "CLAUSE_CODE");

    // ---- Vertex property keys --------------------------------------------------------------------

    /** Scalar literal value (Float/Integer/Boolean). */
    public static final EntityProxy.Concept OPERAND_VALUE = concept("Clause operand value", "OPERAND_VALUE");
    /** UCUM unit string for a Quantity literal. */
    public static final EntityProxy.Concept OPERAND_UNIT = concept("Clause operand unit (UCUM)", "OPERAND_UNIT");
    /** Referenced concept: the Layer-1 class of a ValueSetRef/Retrieve filter, or a Code literal. */
    public static final EntityProxy.Concept OPERAND_CONCEPT = concept("Clause operand concept", "OPERAND_CONCEPT");
    /** Property/field path (e.g. {@code value}) or a Retrieve alias. */
    public static final EntityProxy.Concept RETRIEVE_PATH = concept("Clause path or alias", "RETRIEVE_PATH");
    /** Named source label for a Property (e.g. a CQL define like {@code "BMI determination"}). */
    public static final EntityProxy.Concept SOURCE_LABEL = concept("Clause source label", "SOURCE_LABEL");
    /** Retrieve resource type (e.g. a FHIR/QICore resource or an ANF topic). */
    public static final EntityProxy.Concept RESOURCE_TYPE = concept("Clause resource type", "RESOURCE_TYPE");

    /** All authored concepts, in bootstrap order (meaning/purpose, root, operators, property keys). */
    public static EntityProxy.Concept[] allConcepts() {
        return new EntityProxy.Concept[]{
                COMPUTABLE_LOGIC, COMPLEX_CONCEPT_CLAUSE, CLAUSE_DEFINITION_GRAPH, CLAUSE_DEFINITION_ROOT,
                CLAUSE_AND, CLAUSE_OR, CLAUSE_NOT,
                CLAUSE_EQUAL, CLAUSE_GREATER, CLAUSE_GREATER_OR_EQUAL, CLAUSE_LESS, CLAUSE_LESS_OR_EQUAL,
                CLAUSE_EXISTS, CLAUSE_IN, CLAUSE_VALUE_SET_REF, CLAUSE_RETRIEVE, CLAUSE_PROPERTY,
                CLAUSE_COUNT, CLAUSE_SUM, CLAUSE_INTERVAL_IN, CLAUSE_INCLUDED_IN, CLAUSE_OVERLAPS,
                CLAUSE_QUANTITY, CLAUSE_INTEGER, CLAUSE_BOOLEAN, CLAUSE_CODE,
                OPERAND_VALUE, OPERAND_UNIT, OPERAND_CONCEPT, RETRIEVE_PATH, SOURCE_LABEL, RESOURCE_TYPE
        };
    }
}
