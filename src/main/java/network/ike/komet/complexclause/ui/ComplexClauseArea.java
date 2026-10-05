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
package network.ike.komet.complexclause.ui;

import dev.ikm.komet.terms.KometTerm;
import dev.ikm.komet.framework.ComponentLookup;
import dev.ikm.komet.framework.view.ViewProperties;
import dev.ikm.komet.layout.KlArea;
import dev.ikm.komet.layout.area.AreaGridSettings;
import dev.ikm.komet.layout.area.KlToolArea;
import dev.ikm.komet.layout.preferences.KlPreferencesFactory;
import dev.ikm.komet.layout_engine.blueprint.StateAndContextBlueprint;
import dev.ikm.komet.layout_engine.blueprint.SupplementalAreaBlueprint;
import dev.ikm.komet.preferences.KometPreferences;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelTerm;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import network.ike.komet.complexclause.ClauseStore;
import network.ike.komet.complexclause.bootstrap.ComplexClauseBootstrap;
import network.ike.komet.complexclause.cql.ClauseToCqlProjector;
import network.ike.komet.complexclause.eval.ClauseEvaluator;
import network.ike.komet.complexclause.eval.EvaluationContext;
import network.ike.komet.complexclause.eval.Verdict;
import network.ike.komet.complexclause.model.Clause;
import network.ike.komet.complexclause.model.ClauseAdaptor;
import network.ike.komet.complexclause.model.ClauseExpression;
import network.ike.komet.complexclause.model.ClauseExpressionBuilder;
import network.ike.komet.complexclause.model.ConceptText;

import java.util.Optional;

/**
 * A Journal tool area for authoring, visualizing, explicating, and evaluating complex-concept
 * clauses. Load a concept (by UUID), see its Layer-2 clause as a tree, project it to CQL (joining
 * the inferred EL++ value set), and evaluate it natively to met / not-met / indeterminate over the
 * open knowledge base. Discovered via {@code provides KlToolArea.Factory} and summoned from the
 * Journal "+" menu.
 */
public final class ComplexClauseArea extends SupplementalAreaBlueprint implements KlToolArea<BorderPane> {

    /** Menu label and window title. */
    static final String TOOL_NAME = "Complex Clause";

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ComplexClauseArea.class);

    private volatile ViewProperties toolViewProperties;
    private volatile Runnable onCloseRequest;

    private TextField conceptField;
    private TreeView<Clause> clauseTree;
    private TextArea cqlOutput;
    private Label verdictBadge;

    private ConceptFacade currentConcept;
    private ClauseExpression currentClause;

    /** Restore constructor (see {@link Factory#restore}). */
    public ComplexClauseArea(KometPreferences preferences) {
        super(preferences);
        buildUi();
    }

    /** Create constructor (see {@link Factory#create}). */
    public ComplexClauseArea(KlPreferencesFactory preferencesFactory, KlArea.Factory areaFactory) {
        super(preferencesFactory, areaFactory);
        buildUi();
    }

    // ---- KlToolArea injection points -------------------------------------------------------------

    @Override
    public void setToolViewProperties(ViewProperties viewProperties) {
        this.toolViewProperties = viewProperties;
    }

    @Override
    public void setOnCloseRequest(Runnable onCloseRequest) {
        this.onCloseRequest = onCloseRequest;
    }

    private ViewCalculator viewCalculator() {
        ViewProperties vp = this.toolViewProperties;
        return vp != null ? vp.calculator() : calculatorForContext();
    }

    // ---- UI construction -------------------------------------------------------------------------

    private void buildUi() {
        BorderPane pane = fxObject();

        conceptField = new TextField();
        conceptField.setPromptText("Concept UUID");
        conceptField.setPrefColumnCount(28);

        Button loadButton = new Button("Load");
        loadButton.setOnAction(e -> onLoad());
        Button sampleButton = new Button("Insert sample");
        sampleButton.setOnAction(e -> onInsertSample());
        Button explicateButton = new Button("Explicate → CQL");
        explicateButton.setOnAction(e -> onExplicate());
        Button evaluateButton = new Button("Evaluate");
        evaluateButton.setOnAction(e -> onEvaluate());
        Button saveButton = new Button("Save clause");
        saveButton.setOnAction(e -> onSave());

        verdictBadge = new Label("—");
        verdictBadge.setStyle("-fx-padding: 2 10 2 10; -fx-background-radius: 10; -fx-background-color: #e0e0e0;");

        ToolBar toolBar = new ToolBar(conceptField, loadButton, sampleButton, explicateButton,
                evaluateButton, saveButton, verdictBadge);

        clauseTree = new TreeView<>();
        clauseTree.setShowRoot(true);
        clauseTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Clause item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : describe(item));
            }
        });

        cqlOutput = new TextArea();
        cqlOutput.setEditable(false);
        cqlOutput.setStyle("-fx-font-family: 'monospace';");
        cqlOutput.setPromptText("Explicated CQL appears here");

        SplitPane split = new SplitPane(clauseTree, cqlOutput);
        split.setDividerPositions(0.45);

        pane.setTop(toolBar);
        pane.setCenter(split);
        BorderPane.setMargin(split, new Insets(4));
    }

    // ---- Actions ---------------------------------------------------------------------------------

    private void onLoad() {
        String text = conceptField.getText() == null ? "" : conceptField.getText().trim();
        if (text.isEmpty()) {
            status("Enter a concept UUID");
            return;
        }
        try {
            ComplexClauseBootstrap.ensureBootstrapped();
            Optional<ConceptFacade> concept = conceptFor(text);
            if (concept.isEmpty()) {
                // Nothing is loaded now: the field no longer names what was loaded before, and a
                // clause must not be saved on a concept the field does not show.
                currentConcept = null;
                currentClause = null;
                clauseTree.setRoot(null);
                cqlOutput.clear();
                status("No concept with that UUID in this knowledge base");
                return;
            }
            currentConcept = concept.get();
            currentClause = ClauseStore.readClause(currentConcept, viewCalculator()).orElse(null);
            if (currentClause == null) {
                status("No clause on this concept — use \"Insert sample\" to start one");
                clauseTree.setRoot(null);
            } else {
                refreshTree();
                status("Loaded clause");
            }
            cqlOutput.clear();
        } catch (IllegalArgumentException e) {
            status("Not a valid UUID");
        } catch (RuntimeException e) {
            LOG.error("Load failed", e);
            status("Load failed: " + e.getMessage());
        }
    }

    private void onInsertSample() {
        if (currentConcept == null) {
            status("Load a concept first");
            return;
        }
        currentClause = sampleClause(currentConcept);
        refreshTree();
        cqlOutput.clear();
        status("Inserted sample clause (not yet saved)");
    }

    private void onExplicate() {
        if (currentClause == null) {
            status("Nothing to explicate");
            return;
        }
        try {
            ClauseToCqlProjector projector = new ClauseToCqlProjector(
                    layer1 -> "<< " + nameOf(layer1), viewCalculator());
            cqlOutput.setText(projector.project(currentClause, nameOf(currentConcept)));
        } catch (RuntimeException e) {
            LOG.error("Explication failed", e);
            cqlOutput.setText("// Explication failed: " + e.getMessage());
        }
    }

    private void onEvaluate() {
        if (currentClause == null) {
            status("Nothing to evaluate");
            return;
        }
        Verdict verdict = new ClauseEvaluator().evaluate(currentClause,
                new EvaluationContext(currentConcept, viewCalculator()));
        showVerdict(verdict);
    }

    private void onSave() {
        if (currentConcept == null || currentClause == null) {
            status("Nothing to save");
            return;
        }
        try {
            DiTreeEntity graph = (DiTreeEntity) currentClause.sourceGraph();
            ClauseStore.writeClause(currentConcept, graph,
                    KernelTerm.USER, KometTerm.DEVELOPMENT_MODULE, KernelTerm.DEVELOPMENT_PATH);
            status("Saved clause");
        } catch (RuntimeException e) {
            LOG.error("Save failed", e);
            status("Save failed: " + e.getMessage());
        }
    }

    private void refreshTree() {
        Clause top = currentClause.expression();
        clauseTree.setRoot(top == null ? null : toTreeItem(top));
    }

    private TreeItem<Clause> toTreeItem(Clause clause) {
        TreeItem<Clause> item = new TreeItem<>(clause);
        item.setExpanded(true);
        for (Clause child : clause.children()) {
            item.getChildren().add(toTreeItem(child));
        }
        return item;
    }

    private void showVerdict(Verdict verdict) {
        String color = switch (verdict) {
            case MET -> "#1b8a3a";
            case NOT_MET -> "#b3261e";
            case INDETERMINATE -> "#8a6d00";
        };
        verdictBadge.setText(verdict.name().replace('_', ' '));
        verdictBadge.setStyle("-fx-text-fill: white; -fx-padding: 2 10 2 10; -fx-background-radius: 10;"
                + " -fx-background-color: " + color + ";");
    }

    private void status(String message) {
        verdictBadge.setText(message);
        verdictBadge.setStyle("-fx-padding: 2 10 2 10; -fx-background-radius: 10; -fx-background-color: #e0e0e0;");
    }

    /**
     * The concept a typed UUID names in the open knowledge base.
     *
     * <p>The UUID comes from outside the knowledge base, so it may name a concept the knowledge
     * base does not hold. Asking the store for its nid would assign one anyway, and the concept
     * would then load, and a clause could be saved on it. The lookup assigns nothing for a UUID
     * the knowledge base does not hold ({@link ComponentLookup}).
     *
     * @param uuidText the text of the concept field: one UUID
     * @return the concept, or empty when the knowledge base does not hold it
     * @throws IllegalArgumentException if the text is not a UUID
     */
    static Optional<ConceptFacade> conceptFor(String uuidText) {
        PublicId publicId = PublicIds.of(uuidText);
        if (ComponentLookup.nid(publicId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(EntityProxy.Concept.make(publicId));
    }

    private String nameOf(ConceptFacade concept) {
        return ConceptText.name(concept, viewCalculator());
    }

    private String describe(Clause clause) {
        return switch (clause) {
            case ClauseAdaptor.RootAdaptor ignored -> "clause";
            case ClauseAdaptor.NaryConnectiveAdaptor connective -> connective.clauseSemantic().name();
            case ClauseAdaptor.NotAdaptor ignored -> "Not";
            case ClauseAdaptor.ComparisonAdaptor comparison -> comparison.clauseSemantic().name();
            case ClauseAdaptor.ExistsAdaptor ignored -> "Exists";
            case ClauseAdaptor.InAdaptor ignored -> "In value set";
            case ClauseAdaptor.ValueSetRefAdaptor reference -> "ValueSetRef: " + nameOf(reference.valueSetConcept());
            case ClauseAdaptor.RetrieveAdaptor retrieve -> "Retrieve: " + nameOf(retrieve.resourceType());
            case ClauseAdaptor.PropertyAdaptor property ->
                    "Property: " + (property.sourceLabel() != null ? property.sourceLabel() + "." : "") + property.path();
            case ClauseAdaptor.AggregateAdaptor aggregate -> aggregate.clauseSemantic().name();
            case ClauseAdaptor.IntervalOpAdaptor interval -> interval.clauseSemantic().name();
            case ClauseAdaptor.LiteralAdaptor literal -> describeLiteral(literal);
        };
    }

    private String describeLiteral(ClauseAdaptor.LiteralAdaptor literal) {
        return switch (literal.clauseSemantic()) {
            case QUANTITY -> "Quantity " + literal.value() + (literal.unit() != null ? " " + literal.unit() : "");
            case INTEGER, BOOLEAN -> "Literal " + literal.value();
            case CODE -> "Code: " + nameOf(literal.code());
            default -> "Literal";
        };
    }

    /** A self-contained demo clause anchored on the loaded concept (coded path OR a BMI threshold). */
    private static ClauseExpression sampleClause(ConceptFacade concept) {
        ClauseExpressionBuilder builder = new ClauseExpressionBuilder();
        Clause codedPath = builder.In(builder.Code(concept), builder.ValueSetRef(concept));
        Clause computedPath = builder.GreaterOrEqual(
                builder.Property("BMI determination", "value"), builder.Quantity(40, "kg/m2"));
        builder.setExpression(builder.Or(codedPath, computedPath));
        return builder.build();
    }

    // ---- AreaBlueprint / KlView lifecycle --------------------------------------------------------

    @Override
    protected void subAreaRestoreFromPreferencesOrDefault() {
        // Clause editing is concept-driven; nothing area-scoped to restore.
    }

    @Override
    protected void subAreaRevert() {
        // Nothing to revert.
    }

    @Override
    protected void subAreaSave() {
        // Nothing area-scoped to persist (clauses persist as semantics, not in area prefs).
    }

    @Override
    public void knowledgeLayoutBind() {
        Platform.runLater(() -> this.lifecycleState.set(StateAndContextBlueprint.LifecycleState.BOUND));
    }

    @Override
    public void knowledgeLayoutUnbind() {
        // Nothing to release.
    }

    /**
     * ServiceLoader factory contributing {@link ComplexClauseArea} as a summonable Journal tool.
     */
    public static final class Factory implements KlToolArea.Factory<BorderPane, ComplexClauseArea> {

        /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
        public Factory() {
            super();
        }

        @Override
        public String toolName() {
            return TOOL_NAME;
        }

        @Override
        public ComplexClauseArea restore(KometPreferences preferences) {
            return new ComplexClauseArea(preferences);
        }

        @Override
        public ComplexClauseArea create(KlPreferencesFactory preferencesFactory, AreaGridSettings areaGridSettings) {
            ComplexClauseArea area = new ComplexClauseArea(preferencesFactory, this);
            area.setAreaLayout(areaGridSettings.with(this.getClass()));
            return area;
        }
    }
}
