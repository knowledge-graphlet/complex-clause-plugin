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
package network.ike.komet.complexclause.eval;

import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.terms.ConceptFacade;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The data the native evaluator runs a clause over — our own Tinkar/ANF substrate, not FHIR. It
 * binds an optional subject component and an optional {@link ViewCalculator} (used for value-set
 * membership via subsumption), plus a map of resolved instance values keyed by a property key
 * (see {@link #propertyKey(String, String)}). Absence of a needed value yields
 * {@link Verdict#INDETERMINATE} rather than a false negative.
 *
 * <p>For v1 the value map is populated by the caller (the tool area reads the subject's value /
 * measurement semantics, and tests inject values directly); richer ANF statement traversal is a
 * follow-up.
 */
public final class EvaluationContext {

    private final ConceptFacade subject;
    private final ViewCalculator calculator;
    private final Map<String, Object> values = new HashMap<>();

    /**
     * @param subject    the component the clause is evaluated about (may be {@code null})
     * @param calculator the view used for subsumption/membership (may be {@code null})
     */
    public EvaluationContext(ConceptFacade subject, ViewCalculator calculator) {
        this.subject = subject;
        this.calculator = calculator;
    }

    /** @return the subject component, or {@code null}. */
    public ConceptFacade subject() {
        return subject;
    }

    /**
     * Injects a resolved instance value for a property key.
     *
     * @param key   the property key (use {@link #propertyKey(String, String)})
     * @param value the value (typically a {@link Number} or {@link Boolean})
     * @return this context
     */
    public EvaluationContext put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    /**
     * Looks up a resolved instance value.
     *
     * @param key the property key
     * @return the value, or empty if absent (drives {@link Verdict#INDETERMINATE})
     */
    public Optional<Object> lookup(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /**
     * Tests value-set membership by subsumption (our substrate's terminology layer).
     *
     * @param conceptNid        the candidate concept
     * @param valueSetClassNid  the Layer-1 class whose subsumption set is the value set
     * @return present {@code true}/{@code false} when determinable, empty when no view is available
     */
    public Optional<Boolean> isMember(int conceptNid, int valueSetClassNid) {
        if (calculator == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(calculator.kindOf(valueSetClassNid).contains(conceptNid));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Canonical key for a property navigation: {@code "<label>.<path>"} or just {@code path}.
     *
     * @param sourceLabel a named source (e.g. a CQL define name), or {@code null}
     * @param path        the field path
     * @return the lookup key
     */
    public static String propertyKey(String sourceLabel, String path) {
        return sourceLabel == null || sourceLabel.isBlank() ? path : sourceLabel + "." + path;
    }
}
