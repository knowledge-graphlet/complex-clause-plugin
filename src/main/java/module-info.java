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

/**
 * Komet plugin for <strong>complex concept clauses</strong> — the Layer-2 / computable-logic
 * criteria that exceed OWL EL++ expressivity (negation, disjunction, quantity comparison, counts,
 * temporal relations).
 *
 * <p>A clause is an expression tree of concepts that mirror HL7 ELM nodes, represented as
 * {@linkplain network.ike.komet.complexclause.model.ClauseAdaptor adaptors} over a
 * {@code DiTree<EntityVertex>} source graph — the same architecture as
 * {@code dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression}. It is stored as a second
 * semantic on the concept (alongside the EL++ stated/inferred axioms), explicated to CQL that joins
 * the inferred EL++ value set with the Layer-2 residual, and evaluated natively over the
 * Tinkar/ANF substrate to met / not-met / indeterminate.
 *
 * <p>The tool area is discovered via {@link java.util.ServiceLoader}: {@code KlToolArea.Factory}
 * surfaces it on the Journal "+" menu and {@code KlArea.Factory} in the knowledge-layout editor.
 * {@code ServiceLifecycle} idempotently bootstraps the pattern + operator vocabulary.
 */
module komet.complexclause {
    // Knowledge-layout SPI: KlToolArea / KlSupplementalArea + SupplementalAreaBlueprint, KlArea,
    // AreaGridSettings, KlPreferencesFactory.
    requires dev.ikm.komet.layout;
    // ViewProperties + ViewCalculator (subsumption, descriptions, inferred axiom tree).
    requires dev.ikm.komet.framework;
    // KometPreferences for the area restore/create constructors.
    requires dev.ikm.komet.preferences;

    // Tinkar substrate.
    requires dev.ikm.tinkar.entity;
    requires dev.ikm.tinkar.common;
    requires dev.ikm.tinkar.component;
    requires dev.ikm.tinkar.terms;
    requires dev.ikm.tinkar.composer;

    requires org.eclipse.collections.api;
    requires org.slf4j;
    requires javafx.controls;

    exports network.ike.komet.complexclause;
    exports network.ike.komet.complexclause.model;
    exports network.ike.komet.complexclause.terms;
    exports network.ike.komet.complexclause.cql;
    exports network.ike.komet.complexclause.eval;

    provides dev.ikm.komet.layout.area.KlToolArea.Factory
            with network.ike.komet.complexclause.ui.ComplexClauseArea.Factory;
    provides dev.ikm.komet.layout.KlArea.Factory
            with network.ike.komet.complexclause.ui.ComplexClauseArea.Factory;
    provides dev.ikm.tinkar.common.service.ServiceLifecycle
            with network.ike.komet.complexclause.bootstrap.ComplexClauseBootstrap;
}
