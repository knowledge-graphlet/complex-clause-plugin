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
package network.ike.komet.complexclause.bootstrap;

import dev.ikm.tinkar.common.service.ServiceLifecycle;
import dev.ikm.tinkar.component.Component;
import dev.ikm.tinkar.composer.Composer;
import dev.ikm.tinkar.composer.Session;
import dev.ikm.tinkar.composer.assembler.ConceptAssembler;
import dev.ikm.tinkar.composer.assembler.PatternAssembler;
import dev.ikm.tinkar.composer.template.FullyQualifiedName;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import network.ike.komet.complexclause.terms.ComplexClauseTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Idempotently composes the complex-clause pattern and its HL7-ELM-mirroring operator vocabulary
 * into the open knowledge base, so clauses can be authored. Runs at application startup via the
 * {@link ServiceLifecycle} SPI (after the datastore is up) and can also be invoked lazily via
 * {@link #ensureBootstrapped()} the first time the tool area is used.
 *
 * <p>Bootstrapping is a no-op once the pattern exists. All concepts use stable type-5 ids
 * ({@link ComplexClauseTerms}), so the vocabulary is identical across machines and re-running is safe.
 */
public class ComplexClauseBootstrap implements ServiceLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ComplexClauseBootstrap.class);

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public ComplexClauseBootstrap() {
    }

    @Override
    public void startup() {
        // ServiceLifecycle contract: a thrown exception aborts application startup. The clause
        // vocabulary is not load-bearing for the rest of Komet, so failure must not be fatal.
        try {
            ensureBootstrapped();
        } catch (RuntimeException e) {
            LOG.error("Complex-clause vocabulary bootstrap failed", e);
        }
    }

    @Override
    public void shutdown() {
        // Nothing to release.
    }

    /** @return {@code true} once the complex-clause pattern is present in the datastore. */
    public static boolean isBootstrapped() {
        return EntityService.get()
                .getEntity((Component) ComplexClauseTerms.COMPLEX_CONCEPT_CLAUSE_PATTERN).isPresent();
    }

    /**
     * Composes the pattern + operator vocabulary if not already present.
     *
     * @return {@code true} if a bootstrap was performed, {@code false} if it was already present
     */
    public static synchronized boolean ensureBootstrapped() {
        if (isBootstrapped()) {
            return false;
        }
        LOG.info("Bootstrapping complex-clause pattern + ELM operator vocabulary");
        Composer composer = new Composer("complex-clause-bootstrap");
        Session session = composer.open(State.ACTIVE, TinkarTerm.USER,
                TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);

        for (EntityProxy.Concept proxy : ComplexClauseTerms.allConcepts()) {
            final EntityProxy.Concept concept = proxy;
            session.compose((ConceptAssembler assembler) -> assembler.concept(concept)
                    .attach(FullyQualifiedName.class, fqn -> fqn
                            .language(TinkarTerm.ENGLISH_LANGUAGE)
                            .text(concept.description())
                            .caseSignificance(TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE)));
        }

        session.compose((PatternAssembler assembler) -> assembler
                .pattern(ComplexClauseTerms.COMPLEX_CONCEPT_CLAUSE_PATTERN)
                .meaning(ComplexClauseTerms.COMPLEX_CONCEPT_CLAUSE)
                .purpose(ComplexClauseTerms.COMPUTABLE_LOGIC)
                .fieldDefinition(ComplexClauseTerms.CLAUSE_DEFINITION_GRAPH,
                        ComplexClauseTerms.COMPUTABLE_LOGIC, TinkarTerm.DITREE_FIELD)
                .attach(FullyQualifiedName.class, fqn -> fqn
                        .language(TinkarTerm.ENGLISH_LANGUAGE)
                        .text("Complex Concept Clause Pattern")
                        .caseSignificance(TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE)));

        composer.commitSession(session);
        LOG.info("Complex-clause vocabulary bootstrap committed: {} concepts + 1 pattern",
                ComplexClauseTerms.allConcepts().length);
        return true;
    }
}
