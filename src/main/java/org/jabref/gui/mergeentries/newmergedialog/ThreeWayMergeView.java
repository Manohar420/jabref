package org.jabref.gui.mergeentries.newmergedialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.undo.CompoundEdit;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;

import org.jabref.gui.actions.SimpleCommand;
import org.jabref.gui.mergeentries.newmergedialog.cell.FieldNameCell;
import org.jabref.gui.mergeentries.newmergedialog.cell.FieldNameCellFactory;
import org.jabref.gui.mergeentries.newmergedialog.cell.GroupsFieldNameCell;
import org.jabref.gui.mergeentries.newmergedialog.toolbar.ThreeWayMergeToolbar;
import org.jabref.gui.undo.UndoableFieldChange;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.InternalField;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.EntryTypeFactory;
import org.jabref.model.strings.StringUtil;

public class ThreeWayMergeView extends VBox {
    public static final String GROUPS_SEPARATOR = ", ";
    public static final int GRID_COLUMN_MIN_WIDTH = 250;

    public static final String LEFT_DEFAULT_HEADER = Localization.lang("Left Entry");
    public static final String RIGHT_DEFAULT_HEADER = Localization.lang("Right Entry");

    private static final int FIELD_NAME_COLUMN = 0;
    private final ColumnConstraints fieldNameColumnConstraints = new ColumnConstraints(150);
    private final ColumnConstraints leftEntryColumnConstraints = new ColumnConstraints(GRID_COLUMN_MIN_WIDTH, 256, Double.MAX_VALUE);
    private final ColumnConstraints rightEntryColumnConstraints = new ColumnConstraints(GRID_COLUMN_MIN_WIDTH, 256, Double.MAX_VALUE);
    private final ColumnConstraints mergedEntryColumnConstraints = new ColumnConstraints(GRID_COLUMN_MIN_WIDTH, 256, Double.MAX_VALUE);
    private final ThreeWayMergeToolbar toolbar;
    private final ThreeWayMergeHeaderView headerView;
    private final ScrollPane scrollPane;
    private final GridPane mergeGridPane;

    private final ThreeWayMergeViewModel viewModel;
    private final List<ThreeFieldValues> fieldValuesList = new ArrayList<>();

    private final BooleanProperty areGroupsMerged = new SimpleBooleanProperty();

    private final CompoundEdit mergeGroupsEdit = new CompoundEdit();

    public ThreeWayMergeView(BibEntry leftEntry, BibEntry rightEntry, String leftHeader, String rightHeader) {
        getStylesheets().add(ThreeWayMergeView.class.getResource("ThreeWayMergeView.css").toExternalForm());
        viewModel = new ThreeWayMergeViewModel((BibEntry) leftEntry.clone(), (BibEntry) rightEntry.clone(), leftHeader, rightHeader);

        mergeGridPane = new GridPane();
        scrollPane = new ScrollPane();
        headerView = new ThreeWayMergeHeaderView(leftHeader, rightHeader);
        toolbar = new ThreeWayMergeToolbar();

        initializeColumnConstraints();
        initializeMergeGridPane();
        initializeScrollPane();
        initializeHeaderView();
        initializeToolbar();

        this.setPrefHeight(Screen.getPrimary().getBounds().getHeight() * 0.76);
        this.setPrefWidth(Screen.getPrimary().getBounds().getWidth() * 0.97);

        getChildren().addAll(toolbar, headerView, scrollPane);
    }

    public ThreeWayMergeView(BibEntry leftEntry, BibEntry rightEntry) {
        this(leftEntry, rightEntry, LEFT_DEFAULT_HEADER, RIGHT_DEFAULT_HEADER);
    }

    private void initializeToolbar() {
        toolbar.setOnSelectLeftEntryValuesButtonClicked(this::selectLeftEntryValues);
        toolbar.setOnSelectRightEntryValuesButtonClicked(this::selectRightEntryValues);

        toolbar.showDiffProperty().addListener(e -> updateDiff());
        toolbar.diffViewProperty().addListener(e -> updateDiff());
        toolbar.diffHighlightingMethodProperty().addListener(e -> updateDiff());
    }

    private void updateDiff() {
        if (toolbar.isShowDiffEnabled()) {
            fieldValuesList.forEach(fieldValues -> fieldValues.showDiff(new ShowDiffConfig(toolbar.getDiffView(), toolbar.getDiffHighlightingMethod())));
        } else {
            fieldValuesList.forEach(ThreeFieldValues::hideDiff);
        }
    }

    private void initializeHeaderView() {
        headerView.getColumnConstraints().addAll(fieldNameColumnConstraints,
                                                 leftEntryColumnConstraints,
                                                 rightEntryColumnConstraints,
                                                 mergedEntryColumnConstraints);
    }

    private void initializeScrollPane() {
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setContent(mergeGridPane);
    }

    private void initializeColumnConstraints() {
        fieldNameColumnConstraints.setHgrow(Priority.NEVER);
        leftEntryColumnConstraints.setHgrow(Priority.ALWAYS);
        rightEntryColumnConstraints.setHgrow(Priority.ALWAYS);
        mergedEntryColumnConstraints.setHgrow(Priority.ALWAYS);
    }

    private void initializeMergeGridPane() {
        mergeGridPane.getColumnConstraints().addAll(fieldNameColumnConstraints, leftEntryColumnConstraints, rightEntryColumnConstraints, mergedEntryColumnConstraints);

        for (int fieldIndex = 0; fieldIndex < viewModel.allFieldsSize(); fieldIndex++) {
            addFieldName(getFieldAtIndex(fieldIndex), fieldIndex);
            addFieldValues(fieldIndex);

            // Removing this will cause UI to lag when updating field values
            if (getFieldAtIndex(fieldIndex).equals(StandardField.GROUPS)) {
                mergeGridPane.getRowConstraints().add(fieldIndex, new RowConstraints(56, 56, Double.MAX_VALUE));
            } else {
                mergeGridPane.getRowConstraints().add(new RowConstraints());
            }
        }
    }

    private void addFieldName(Field field, int fieldIndex) {
        FieldNameCell fieldNameCell = FieldNameCellFactory.create(field, fieldIndex);
        mergeGridPane.add(fieldNameCell, FIELD_NAME_COLUMN, fieldIndex);

        if (field.equals(StandardField.GROUPS)) {
            GroupsFieldNameCell groupsField = (GroupsFieldNameCell) fieldNameCell;
            groupsField.setMergeGroupsCommand(new MergeGroupsCommand(groupsField));
            groupsField.setUnmergeGroupsCommand(new UnmergeGroupsCommand(groupsField));
            groupsField.setMergeAction(GroupsFieldNameCell.MergeAction.MERGE);
        }
    }

    private Field getFieldAtIndex(int index) {
        return viewModel.allFields().get(index);
    }

    private void addFieldValues(int fieldIndex) {
        Field field = getFieldAtIndex(fieldIndex);
        String leftEntryValue;
        String rightEntryValue;
        if (field.equals(InternalField.TYPE_HEADER)) {
            leftEntryValue = viewModel.getLeftEntry().getType().getDisplayName();
            rightEntryValue = viewModel.getRightEntry().getType().getDisplayName();
        } else {
            leftEntryValue = viewModel.getLeftEntry().getField(field).orElse("");
            rightEntryValue = viewModel.getRightEntry().getField(field).orElse("");
        }

        ThreeFieldValues fieldValues = new ThreeFieldValues(leftEntryValue, rightEntryValue, fieldIndex);
        fieldValuesList.add(fieldIndex, fieldValues);

        fieldValues.mergedValueProperty().addListener((observable, old, mergedValue) -> {
            if (field.equals(InternalField.TYPE_HEADER)) {
                getMergedEntry().setType(EntryTypeFactory.parse(mergedValue));
            } else {
                getMergedEntry().setField(field, mergedValue);
            }
        });
        if (field.equals(InternalField.TYPE_HEADER)) {
            getMergedEntry().setType(EntryTypeFactory.parse(fieldValues.getMergedValue()));
        } else {
            getMergedEntry().setField(field, fieldValues.getMergedValue());
        }

        if (fieldValues.hasEqualLeftAndRightValues()) {
            mergeGridPane.add(fieldValues.getLeftValueCell(), 1, fieldIndex, 2, 1);
            mergeGridPane.add(fieldValues.getMergedValueCell(), 3, fieldIndex);
        } else {
            mergeGridPane.add(fieldValues.getLeftValueCell(), 1, fieldIndex);
            mergeGridPane.add(fieldValues.getRightValueCell(), 2, fieldIndex);
            mergeGridPane.add(fieldValues.getMergedValueCell(), 3, fieldIndex);
        }
    }

    private void updateFieldValues(int fieldIndex) {
        removeFieldValues(fieldIndex);
        addFieldValues(fieldIndex);
        updateDiff();
    }

    private void removeFieldValues(int index) {
        fieldValuesList.remove(index);
        mergeGridPane.getChildren().removeIf(node -> GridPane.getRowIndex(node) == index && GridPane.getColumnIndex(node) > FIELD_NAME_COLUMN);
    }

    public BibEntry getMergedEntry() {
        return viewModel.getMergedEntry();
    }

    public void setLeftHeader(String leftHeader) {
        headerView.setLeftHeader(leftHeader);
    }

    public void setRightHeader(String rightHeader) {
        headerView.setRightHeader(rightHeader);
    }

    public void selectLeftEntryValues() {
        fieldValuesList.forEach(ThreeFieldValues::selectLeftValue);
    }

    public void selectRightEntryValues() {
        fieldValuesList.forEach(ThreeFieldValues::selectRightValue);
    }

    public void showDiff(ShowDiffConfig diffConfig) {
        toolbar.setDiffView(diffConfig.diffView());
        toolbar.setDiffHighlightingMethod(diffConfig.diffHighlightingMethod());
        toolbar.setShowDiff(true);
    }

    public void cancelGroupsMerge() {
        if (mergeGroupsEdit.canUndo()) {
            mergeGroupsEdit.undo();
        }
    }

    public BibEntry getLeftEntry() {
        return viewModel.getLeftEntry();
    }

    public BibEntry getRightEntry() {
        return viewModel.getRightEntry();
    }

    public class MergeGroupsCommand extends SimpleCommand {
        private final GroupsFieldNameCell groupsFieldNameCell;

        public MergeGroupsCommand(GroupsFieldNameCell groupsFieldCell) {
            this.groupsFieldNameCell = groupsFieldCell;

            this.executable.bind(Bindings.and(areGroupsMerged.not(), Bindings.createBooleanBinding(() -> {
                String leftEntryGroups = viewModel.getLeftEntry().getField(StandardField.GROUPS).orElse("");
                String rightEntryGroups = viewModel.getRightEntry().getField(StandardField.GROUPS).orElse("");

                return !leftEntryGroups.equals(rightEntryGroups);
            })));
        }

        @Override
        public void execute() {
            BibEntry leftEntry = viewModel.getLeftEntry();
            BibEntry rightEntry = viewModel.getRightEntry();

            String leftEntryGroups = leftEntry.getField(StandardField.GROUPS).orElse("");
            String rightEntryGroups = rightEntry.getField(StandardField.GROUPS).orElse("");

            assert !leftEntryGroups.equals(rightEntryGroups);

            String mergedGroups = mergeLeftAndRightEntryGroups(leftEntryGroups, rightEntryGroups);
            viewModel.getLeftEntry().setField(StandardField.GROUPS, mergedGroups);
            viewModel.getRightEntry().setField(StandardField.GROUPS, mergedGroups);

            if (mergeGroupsEdit.canRedo()) {
                mergeGroupsEdit.redo();
            } else {
                mergeGroupsEdit.addEdit(new UndoableFieldChange(leftEntry, StandardField.GROUPS, leftEntryGroups, mergedGroups));
                mergeGroupsEdit.addEdit(new UndoableFieldChange(rightEntry, StandardField.GROUPS, rightEntryGroups, mergedGroups));
                mergeGroupsEdit.end();
            }

            updateFieldValues(viewModel.allFields().indexOf(StandardField.GROUPS));
            groupsFieldNameCell.setMergeAction(GroupsFieldNameCell.MergeAction.UNMERGE);
        }

        private String mergeLeftAndRightEntryGroups(String left, String right) {
            if (StringUtil.isBlank(left)) {
                return right;
            } else if (StringUtil.isBlank(right)) {
                return left;
            } else {
                Set<String> leftGroups = new HashSet<>(Arrays.stream(left.split(", ")).toList());
                List<String> rightGroups = Arrays.stream(right.split(", ")).toList();
                leftGroups.addAll(rightGroups);

                return String.join(GROUPS_SEPARATOR, leftGroups);
            }
        }
    }

    public class UnmergeGroupsCommand extends SimpleCommand {
        private final GroupsFieldNameCell groupsFieldCell;

        public UnmergeGroupsCommand(GroupsFieldNameCell groupsFieldCell) {
            this.groupsFieldCell = groupsFieldCell;
        }

        @Override
        public void execute() {
            if (mergeGroupsEdit.canUndo()) {
                mergeGroupsEdit.undo();
                updateFieldValues(viewModel.allFields().indexOf(StandardField.GROUPS));
                groupsFieldCell.setMergeAction(GroupsFieldNameCell.MergeAction.MERGE);
            }
        }
    }
}