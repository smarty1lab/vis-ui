/*
 * Copyright 2014-2015 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kotcrab.vis.editor.ui.scene;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.editor.Assets;
import com.kotcrab.vis.editor.Icons;
import com.kotcrab.vis.editor.module.InjectModule;
import com.kotcrab.vis.editor.module.ModuleInjector;
import com.kotcrab.vis.editor.module.scene.LayerManipulatorManager;
import com.kotcrab.vis.editor.module.scene.UndoModule;
import com.kotcrab.vis.editor.module.scene.action.EntitiesRemovedAction;
import com.kotcrab.vis.editor.module.scene.entitymanipulator.EntityManipulatorModule;
import com.kotcrab.vis.editor.scene.EditorScene;
import com.kotcrab.vis.editor.scene.Layer;
import com.kotcrab.vis.editor.util.DefaultInputDialogListener;
import com.kotcrab.vis.editor.util.ObservableListener;
import com.kotcrab.vis.editor.util.gdx.EventStopper;
import com.kotcrab.vis.editor.util.gdx.TableBuilder;
import com.kotcrab.vis.editor.util.gdx.VisChangeListener;
import com.kotcrab.vis.editor.util.undo.MonoUndoableAction;
import com.kotcrab.vis.editor.util.undo.UndoableAction;
import com.kotcrab.vis.editor.util.undo.UndoableActionGroup;
import com.kotcrab.vis.runtime.util.BagUtils;
import com.kotcrab.vis.runtime.util.EntityEngineConfiguration;
import com.kotcrab.vis.runtime.util.ImmutableArray;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.dialog.DialogUtils;
import com.kotcrab.vis.ui.util.dialog.DialogUtils.OptionDialogType;
import com.kotcrab.vis.ui.util.dialog.OptionDialogAdapter;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisImageButton.VisImageButtonStyle;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;

/**
 * Dialog displayed in scene tab, allows to manipulate layers
 * @author Kotcrab
 */
public class LayersDialog extends VisTable implements Disposable {
	private static final Drawable SELECTION = VisUI.getSkin().getDrawable("list-selection");
	private static final VisImageButtonStyle BUTTON_STYLE = VisUI.getSkin().get("default", VisImageButtonStyle.class);
	private static final VisImageButtonStyle BUTTON_BLUE_STYLE = VisUI.getSkin().get("blue", VisImageButtonStyle.class);

	@InjectModule private UndoModule undoModule;
	@InjectModule private EntityManipulatorModule entityManipulator;

	private LayerManipulatorManager layerManipulatorManager;

	private SceneTab sceneTab;
	private EditorScene scene;

	private VisTable layersTable;
	private VisImageButton layerUpButton;
	private VisImageButton layerDownButton;
	private VisImageButton layerSettingsButton;
	private VisImageButton layerRemoveButton;

	private ObservableListener sceneObservable;

	public LayersDialog (SceneTab sceneTab, EntityEngineConfiguration engineConfig, ModuleInjector sceneMC) {
		super(true);
		this.sceneTab = sceneTab;
		this.scene = sceneTab.getScene();
		sceneMC.injectModules(this);
		layerManipulatorManager = engineConfig.getManager(LayerManipulatorManager.class);

		setBackground(VisUI.getSkin().getDrawable("window-bg"));
		setTouchable(Touchable.enabled);

		VisImageButton layerAddButton = new VisImageButton(Assets.getIcon(Icons.LAYER_ADD));
		layerUpButton = new VisImageButton(Assets.getIcon(Icons.LAYER_UP));
		layerDownButton = new VisImageButton(Assets.getIcon(Icons.LAYER_DOWN));
		layerSettingsButton = new VisImageButton(Assets.getIcon(Icons.SETTINGS));
		layerRemoveButton = new VisImageButton(Assets.getIcon(Icons.LAYER_REMOVE));

		layerUpButton.setGenerateDisabledImage(true);
		layerDownButton.setGenerateDisabledImage(true);
		layerRemoveButton.setGenerateDisabledImage(true);

		layerAddButton.addListener(new VisChangeListener((event, actor) ->
				DialogUtils.showInputDialog(getStage(), "New Layer", "Name:", true,
						input -> scene.getLayerByName(input) == null,
						(DefaultInputDialogListener) input -> undoModule.execute(new LayerAddedAction(input)))));

		layerUpButton.addListener(new VisChangeListener((event, actor) -> undoModule.execute(new LayerMovedAction(true))));
		layerDownButton.addListener(new VisChangeListener((event, actor) -> undoModule.execute(new LayerMovedAction(false))));

		layerSettingsButton.addListener(new VisChangeListener((event1, actor1) -> getStage().addActor(new LayerSettingsDialog(scene).fadeIn())));

		layerRemoveButton.addListener(new VisChangeListener((event, actor) ->
				DialogUtils.showOptionDialog(getStage(), "Delete Layer", "Are you sure you want to delete layer '" + scene.getActiveLayer().name + "'?",
						OptionDialogType.YES_NO, new OptionDialogAdapter() {
							@Override
							public void yes () {
								UndoableActionGroup layerRemovedGroup = new UndoableActionGroup("Delete Layer");
								layerRemovedGroup.add(new EntitiesRemovedAction(sceneMC, sceneTab.getEntityEngine(),
										BagUtils.toSet(layerManipulatorManager.getEntitiesWithLayer(scene.getActiveLayerId()))));
								layerRemovedGroup.add(new LayerRemovedAction(scene.getActiveLayer()));
								layerRemovedGroup.finalizeGroup();

								undoModule.execute(layerRemovedGroup);
							}
						})));

		layersTable = new VisTable();

		VisScrollPane layersScrollPane = new VisScrollPane(layersTable);
		layersScrollPane.setScrollingDisabled(true, false);
		layersScrollPane.setFadeScrollBars(false);

		top();
		left();
		defaults().left();
		defaults().padLeft(5).padRight(5);
		add(new VisLabel("Layers")).center().row();
		add(layersScrollPane).expandX().fillX().row();
		addSeparator();
		add(TableBuilder.build(layerAddButton, layerUpButton, layerDownButton, layerSettingsButton, layerRemoveButton)).padBottom(8);

		addListener(new EventStopper());

		rebuildLayersTable();

		sceneObservable = nid -> {
			if (nid == EditorScene.LAYER_ADDED || nid == EditorScene.LAYER_INSERTED || nid == EditorScene.LAYER_REMOVED
					|| nid == EditorScene.LAYERS_SORTED || nid == EditorScene.ACTIVE_LAYER_CHANGED || nid == EditorScene.LAYER_DATA_CHANGED) {
				rebuildLayersTable();
				sceneTab.dirty();
			}
		};

		scene.addObservable(sceneObservable);
	}

	private void selectedLayer (Layer layer) {
		scene.setActiveLayer(layer.id);

		layerUpButton.setDisabled(false);
		layerDownButton.setDisabled(false);

		ImmutableArray<Layer> layers = scene.getLayers();

		int index = layers.indexOf(layer, true);
		if (index == 0) layerUpButton.setDisabled(true);
		if (index == layers.size() - 1) layerDownButton.setDisabled(true);
	}

	private void rebuildLayersTable () {
		deselectAll();
		Array<Actor> actors = new Array<>(layersTable.getChildren());
		layersTable.clearChildren();

		for (Layer layer : scene.getLayers()) {
			LayerItem item = getItemForLayer(actors, layer);
			if (item == null) item = new LayerItem(layer);
			item.update();
			layersTable.add(item).expandX().fillX().row();

			if (layer == scene.getActiveLayer())
				item.select();
		}

		if (scene.getLayers().size() == 1) {
			layerDownButton.setDisabled(true);
			layerUpButton.setDisabled(true);
			layerRemoveButton.setDisabled(true);
		} else
			layerRemoveButton.setDisabled(false);

	}

	private LayerItem getItemForLayer (Array<Actor> actors, Layer layer) {
		for (Actor a : actors) {
			if (a instanceof LayerItem) {
				LayerItem item = (LayerItem) a;
				if (item.layer == layer) {
					return item;
				}
			}
		}

		return null;
	}

	private void deselectAll () {
		for (Actor a : layersTable.getChildren()) {
			if (a instanceof LayerItem) {
				LayerItem item = (LayerItem) a;
				item.deselect();
			}
		}
	}

	private void selectFirstLayer () {
		selectLayer(scene.getLayers().first());
	}

	private void selectLayer (Layer layer) {
		LayerItem item = getItemForLayer(layersTable.getChildren(), layer);
		if (item == null) throw new IllegalStateException("Layer not found");

		//selectedLayer would by already called from item.select
		item.select();
	}

	@Override
	public void dispose () {
		scene.removeObservable(sceneObservable);
	}

	private class LayerAddedAction implements UndoableAction {
		private String name;

		private Layer layer;

		public LayerAddedAction (String name) {
			this.name = name;
		}

		@Override
		public void execute () {
			layer = scene.addLayer(name);
			selectLayer(layer);
		}

		@Override
		public void undo () {
			scene.removeLayer(layer);
		}

		@Override
		public String getActionName () {
			return "Add Layer";
		}
	}

	private class LayerRemovedAction implements UndoableAction {
		private Layer layer;

		public LayerRemovedAction (Layer layer) {
			this.layer = layer;
		}

		@Override
		public void execute () {
			scene.removeLayer(layer);
		}

		@Override
		public void undo () {
			scene.insertLayer(layer);
			selectLayer(layer);
		}

		@Override
		public String getActionName () {
			return "Remove Layer";
		}
	}

	private class LayerMovedAction extends MonoUndoableAction {
		Layer currentLayer;
		Layer targetLayer;

		public LayerMovedAction (boolean moveUp) {
			int currentIndex = scene.getLayers().indexOf(scene.getActiveLayer(), true);
			int targetIndex;

			if (moveUp)
				targetIndex = currentIndex - 1;
			else
				targetIndex = currentIndex + 1;

			currentLayer = scene.getLayers().get(currentIndex);
			targetLayer = scene.getLayers().get(targetIndex);
		}

		@Override
		public void doAction () {
			layerManipulatorManager.swapLayers(currentLayer.id, targetLayer.id);

			int oldCurrentId = currentLayer.id;
			currentLayer.id = targetLayer.id;
			targetLayer.id = oldCurrentId;

			scene.forceSortLayers();

			selectLayer(currentLayer);
		}

		@Override
		public String getActionName () {
			return "Move Layer";
		}
	}

	private class LayerItem extends VisTable {
		private Layer layer;

		private VisLabel nameLabel;
		private VisImageButton eyeButton;
		private VisImageButton lockButton;

		public LayerItem (Layer layer) {
			super(true);
			this.layer = layer;

			setTouchable(Touchable.enabled);

			eyeButton = new VisImageButton("default");
			lockButton = new VisImageButton("default");
			updateButtonsImages();

			eyeButton.addListener(new VisChangeListener((event, actor) -> undoModule.execute(new MonoUndoableAction() {
				@Override
				public String getActionName () {
					return "Hide/Show Layer";
				}

				@Override
				public void doAction () {
					changeVisibility();
				}
			})));

			lockButton.addListener(new VisChangeListener((event, actor) -> undoModule.execute(new MonoUndoableAction() {
				@Override
				public String getActionName () {
					return "Lock/Unlock Layer";
				}

				@Override
				public void doAction () {
					changeLocked();
				}
			})));

			pad(3);
			add(eyeButton);
			add(lockButton);
			add(nameLabel = new VisLabel(layer.name)).expandX().fillX(); //TODO: show id in layers dialog, don't forget about updating it when layer was moved

			addListener(new InputListener() {
				@Override
				public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
					select();
					return true;
				}
			});
		}

		public void update () {
			nameLabel.setText(layer.name);
		}

		void changeVisibility () {
			layer.visible = !layer.visible;
			layerManipulatorManager.changeLayerVisibility(layer.id, layer.visible);
			updateButtonsImages();
			sceneTab.dirty();
		}

		void changeLocked () {
			layer.locked = !layer.locked;
			updateButtonsImages();

			if (layer.locked)
				entityManipulator.resetSelection();

			sceneTab.dirty();
		}

		private void updateButtonsImages () {
			eyeButton.getStyle().imageUp = layer.visible ? Assets.getIcon(Icons.EYE) : Assets.getIcon(Icons.EYE_DISABLED);
			lockButton.getStyle().imageUp = layer.locked ? Assets.getIcon(Icons.LOCKED) : Assets.getIcon(Icons.UNLOCKED);
		}

		public void select () {
			deselectAll();
			eyeButton.getStyle().over = BUTTON_BLUE_STYLE.over;
			lockButton.getStyle().over = BUTTON_BLUE_STYLE.over;
			eyeButton.getStyle().up = BUTTON_BLUE_STYLE.up;
			lockButton.getStyle().up = BUTTON_BLUE_STYLE.up;
			eyeButton.getStyle().down = BUTTON_BLUE_STYLE.down;
			lockButton.getStyle().down = BUTTON_BLUE_STYLE.down;
			setBackground(SELECTION);

			selectedLayer(layer);
		}

		public void deselect () {
			setBackground((Drawable) null);
			eyeButton.getStyle().over = BUTTON_STYLE.over;
			lockButton.getStyle().over = BUTTON_STYLE.over;
			eyeButton.getStyle().up = BUTTON_STYLE.up;
			lockButton.getStyle().up = BUTTON_STYLE.up;
			eyeButton.getStyle().down = BUTTON_STYLE.down;
			lockButton.getStyle().down = BUTTON_STYLE.down;
		}
	}
}
