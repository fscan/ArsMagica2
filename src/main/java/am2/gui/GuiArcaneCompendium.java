package am2.gui;

import static net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import am2.api.ArsMagicaAPI;
import am2.api.blocks.MultiblockGroup;
import am2.api.blocks.MultiblockStructureDefinition;
import am2.api.blocks.TypedMultiblockGroup;
import am2.api.event.SpellRecipeItemsEvent;
import am2.api.skill.Skill;
import am2.api.spell.AbstractSpellPart;
import am2.defs.ItemDefs;
import am2.gui.controls.GuiButtonCompendiumNext;
import am2.gui.controls.GuiButtonCompendiumTab;
import am2.gui.controls.GuiButtonVariableDims;
import am2.items.ItemSpellComponent;
import am2.lore.ArcaneCompendium;
import am2.lore.CompendiumEntry;
import am2.lore.CompendiumEntrySpellModifier;
import am2.power.PowerTypes;
import am2.rituals.IRitualInteraction;
import am2.texture.SpellIconManager;
import am2.utils.RecipeUtils;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

@SuppressWarnings("deprecation")
public class GuiArcaneCompendium extends GuiScreen {
	
	//Global variables
	private static final ResourceLocation background = new ResourceLocation("arsmagica2", "textures/gui/ArcaneCompendiumGui.png");
	private static final ResourceLocation extras = new ResourceLocation("arsmagica2", "textures/gui/ArcaneCompendiumGuiExtras.png");
	private static final ResourceLocation red = new ResourceLocation("arsmagica2", "textures/blocks/red.png");
	private static final HashMap<Item, Integer> forcedMetas = new HashMap<>();
	public static final int maxLines = 17;
	public static final int lineWidth = 140;
	private static final int xSize = 360;
	private static final int ySize = 256;
	
	//Local vars
	private final CompendiumEntry entry;
	private ArrayList<String> lines = new ArrayList<>();
	private int page = 0;
	private int numPages = 0;
	private IBlockState entryBlock = null;
	private ItemStack entryItem = null;
	private Object[] craftingComponents;
	private Skill entrySkill;
	private int recipeWidth;
	private int recipeHeight;
	private int tipY;
	private int tipX;
	private ItemStack stackTip;
	private float framecount = 0;
	private Entity entryEntity;
	private MultiblockStructureDefinition entryMultiblock;
	private ArrayList<ItemStack> modifiers = new ArrayList<>();
	private float curRotationH;
	private int curLayer = -1;
	private GuiBlockAccess blockAccess = new GuiBlockAccess();
	private GuiButtonCompendiumNext prevPage;
	private GuiButtonCompendiumNext nextPage;
	private GuiButtonCompendiumTab backToIndex;
	private GuiButtonCompendiumNext prevLayer;
	private GuiButtonCompendiumNext nextLayer;
	private GuiButtonVariableDims pauseCycling;
//	private TileEntity entryTileEntity;
	private int maxLayers;
	
	public GuiArcaneCompendium(String id, MultiblockStructureDefinition definition, TileEntity te) {
		this(id);
		this.entryMultiblock = definition;
//		this.entryTileEntity = te;
	}

	public GuiArcaneCompendium(String id) {
		entry = ArcaneCompendium.getCompendium().get(id);
		if (entry != null) {
			lines = entry.getPages();
			numPages = entry.getPages().size() - 1;
		}
	}

	public GuiArcaneCompendium(String id, IBlockState state) {
		this(id, Item.getItemFromBlock(state.getBlock()), state.getBlock().getMetaFromState(state));
		this.entryBlock = state;
	}

	public GuiArcaneCompendium(String id, Entity entity) {
		this(id);
		this.entryEntity = entity;
	}

	public GuiArcaneCompendium(String id, Item item, int meta) {
		this(id);
		this.entryItem = new ItemStack(item, 1, meta);
		getAndAnalyzeRecipe();
	}

	public GuiArcaneCompendium(String id, MultiblockStructureDefinition ritualShape, IRitualInteraction ritualController) {
		this(id);
		this.entryMultiblock = ritualShape;
	}

	public GuiArcaneCompendium(String id, Skill skill) {
		this(id, ItemDefs.spell_component, ArsMagicaAPI.getSkillRegistry().getId(skill));
		this.entrySkill = skill;
		getAndAnalyzeRecipe();
	}
	
	public GuiArcaneCompendium(String id, Skill skill, ArrayList<ItemStack> mods) {
		this(id, skill);
		for (ItemStack stack : mods) {
			if (ArcaneCompendium.For(Minecraft.getMinecraft().thePlayer).isUnlocked(ArsMagicaAPI.getSkillRegistry().getObjectById(stack.getItemDamage()).getID()))
				this.modifiers.add(stack);			
		}
	}
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		framecount += 0.5f;

		framecount %= 360;

		int l = (width - xSize) / 2;
		int i1 = (height - ySize) / 2;

		stackTip = null;
		GL11.glColor3f(1.0f, 1.0f, 1.0f);
		mc.renderEngine.bindTexture(background);
		this.drawTexturedModalRect_Classic(l, i1, 0, 0, xSize, ySize, 256, 240);

		GL11.glPushMatrix();
		
		drawLeftPage(l, i1);
		drawRightPage(l, i1, mouseX, mouseY);
		
		GL11.glPopMatrix();
		
		if (this.page == 0)
			prevPage.visible = false;
		else
			prevPage.visible = true;


		if (this.page == numPages)
			nextPage.visible = false;
		else
			nextPage.visible = true;

		RenderHelper.disableStandardItemLighting();

		GL11.glPushMatrix();
		GL11.glTranslatef(0, 0, -1);
		//GlStateManager.disableDepth();

		this.drawDefaultBackground();
		GL11.glColor3f(1.0f, 1.0f, 1.0f);

		drawRightPageExtras(l, i1);
		//GlStateManager.enableDepth();
		GL11.glPopMatrix();

		RenderHelper.enableStandardItemLighting();

		super.drawScreen(mouseX, mouseY, partialTicks);

		if (stackTip != null){
			renderItemToolTip(stackTip, tipX, tipY);
		}

		if (this.entryMultiblock != null){
			fontRendererObj.drawString(I18n.translateToLocal("am2.gui.mbb"), l + 190, i1 + 195, 0x000000);
		}
	}
	
	private void drawLeftPage(int l, int i1){

		if (entry == null) return;

		int y_start_title = i1 + 50;
		int x_start_title = l + 100 - (fontRendererObj.getStringWidth(entrySkill != null ? entrySkill.getName() : entry.getName()) / 2);

		int x_start_line = l + 35;
		int y_start_line = page == 0 ? i1 + 65 : i1 + 50;

		if (page > numPages) page = numPages;

		if (entry != null){
			if (page == 0)
				fontRendererObj.drawString(entrySkill != null ? entrySkill.getName() : entry.getName(), x_start_title, y_start_title, 0x000000);
			AMGuiHelper.drawCompendiumText(lines.get(page), x_start_line, y_start_line, lineWidth, 0x000000, fontRendererObj);
		}
	}
	
	private void drawRightPage(int l, int i1, int mousex, int mousey){
		int cx = l + 250;
		int cy = i1 + 120 - 6;
	
		if (entryItem != null){
			drawRightPage_Block_Item(cx, cy, mousex, mousey);
		}else if (entryEntity != null){
			drawRightPage_Entity(cx, cy);
		}else if (entryMultiblock != null){
			drawRightPage_Multiblock(cx, cy, mousex, mousey);
		}
	
		drawRelatedItems(cx, cy, mousex, mousey);
		if (modifiers.size() > 0) {
			drawModifiers(cx, entryMultiblock == null ? cy : cy + 20, mousex, mousey);
		}
	}
	
	private void drawRightPage_Block_Item(int cx, int cy, int mousex, int mousey){
		RenderHelper.disableStandardItemLighting();

		if (craftingComponents == null){
			if (mousex > cx && mousex < cx + 16){
				if (mousey > cy && mousey < cy + 16){
					stackTip = this.entryItem;
					tipX = mousex;
					tipY = mousey;
				}
			}
		}else{
			RenderRecipe(cx, cy, mousex, mousey);
		}
		if (this.entryItem.getItem() instanceof ItemSpellComponent){
			TextureAtlasSprite icon = SpellIconManager.INSTANCE.getSprite(entrySkill.getID());
			mc.renderEngine.bindTexture(LOCATION_BLOCKS_TEXTURE);
			GL11.glColor4f(1, 1, 1, 1);
			if (icon != null)
				AMGuiHelper.DrawIconAtXY(icon, cx, cy, zLevel, 16, 16, false);
		} else if (craftingComponents == null){
			AMGuiHelper.DrawItemAtXY(entryItem, cx, cy, this.zLevel);		
		}
		
		if (mousex > cx && mousex < cx + 16){
			if (mousey > cy && mousey < cy + 16){
				stackTip = this.entryItem;
				tipX = mousex;
				tipY = mousey;
			}
		}
		RenderHelper.enableStandardItemLighting();
	}
	
	private void RenderRecipe(int cx, int cy, int mousex, int mousey){
		int step = 32;
		int sx = cx - step;
		int sy = cy - step;
	
		if (craftingComponents == null) return;
	
		if (this.entryItem.getItem() == ItemDefs.essence
//				|| this.entryItem.getItem() == ItemDefs.deficitCrystal
				){
			renderCraftingComponent(0, cx, cy - 36, mousex, mousey);
			renderCraftingComponent(1, cx - 30, cy - 2, mousex, mousey);
			renderCraftingComponent(2, cx, cy - 2, mousex, mousey);
			renderCraftingComponent(3, cx + 28, cy - 2, mousex, mousey);
			renderCraftingComponent(4, cx, cy + 30, mousex, mousey);
			return;
		}
		else if (this.entryItem.getItem() == ItemDefs.spell_component){
			float angleStep = (360.0f / craftingComponents.length);
			for (int i = 0; i < craftingComponents.length; ++i){
				//LogHelper.info(framecount);
				float angle = (float)(Math.toRadians((angleStep * i) + framecount % 360));
				float nextangle = (float)(Math.toRadians((angleStep * ((i + 1) % craftingComponents.length)) + framecount % 360));
				float dist = 45;
				int x = (int)Math.round(cx - Math.cos(angle) * dist);
				int y = (int)Math.round(cy - Math.sin(angle) * dist);
				int nextx = (int)Math.round(cx - Math.cos(nextangle) * dist);
				int nexty = (int)Math.round(cy - Math.sin(nextangle) * dist);
				AMGuiHelper.line2d(x + 8, y + 8, cx + 8, cy + 8, zLevel, 0x0000DD);
				AMGuiHelper.gradientline2d(x + 8, y + 8, nextx + 8, nexty + 8, zLevel, 0x0000DD, 0xDD00DD);
				renderCraftingComponent(i, x, y, mousex, mousey);
			}
			return;
		}
	
		if (recipeHeight > 0){
			for (int i = 0; i < recipeHeight; ++i){
				for (int j = 0; j < recipeWidth; ++j){
					int index = i * recipeWidth + j;
	
					renderCraftingComponent(index, sx, sy, mousex, mousey);
					sx += step;
				}
	
				sx = cx - step;
				sy += step;
			}
		}else{
			int col = 0;
			int row = 0;
			int widthSq = (int)Math.ceil(Math.sqrt(recipeWidth));
	
			String label = "\247nShapeless";
			fontRendererObj.drawString(label, cx - (int)(fontRendererObj.getStringWidth(label) / 2.5), cy - step * 3, 0x6600FF);
	
			for (int i = 0; i < recipeWidth; ++i){
				sx = cx - step + (step * col);
				sy = cy - step + (step * row);
				int index = (row * widthSq) + (col++);
				if (col >= widthSq){
					row++;
					col = 0;
				}
	
				renderCraftingComponent(index, sx, sy, mousex, mousey);
			}
		}
	
		sx = cx;
		sy = cy - (int)(2.5 * step);
	
		if (entryItem != null){
			AMGuiHelper.DrawItemAtXY(entryItem, sx, sy, this.zLevel);
	
			if (entryItem.stackSize > 1)
				fontRendererObj.drawString("x" + entryItem.stackSize, sx + 16, sy + 8, 0, false);
	
			if (mousex > sx && mousex < sx + 16){
				if (mousey > sy && mousey < sy + 16){
					stackTip = this.entryItem;
					tipX = mousex;
					tipY = mousey;
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void renderCraftingComponent(int index, int sx, int sy, int mousex, int mousey){
		Object craftingComponent = craftingComponents[index];
	
		if (craftingComponent == null) return;
	
		ItemStack stack = null;
	
		if (craftingComponent instanceof ItemStack){
			stack = (ItemStack)craftingComponent;
		}else if (craftingComponent instanceof List){
			if (((List<ItemStack>)craftingComponent).size() == 0)
				return;
			int idx = new Random(AMGuiHelper.instance.getSlowTicker()).nextInt(((List<ItemStack>)craftingComponent).size());
			stack = ((ItemStack)((List<ItemStack>)craftingComponent).get(idx)).copy();
		}
	
		List<ItemStack> oredict = OreDictionary.getOres(stack.getItem().getUnlocalizedName());
		List<ItemStack> alternates = new ArrayList<ItemStack>();
		alternates.addAll(oredict);
	
		if (alternates.size() > 0){
			alternates.add(stack);
			stack = alternates.get(new Random(AMGuiHelper.instance.getSlowTicker()).nextInt(alternates.size()));
		}
		if (forcedMetas.containsKey(stack.getItem()))
			stack = new ItemStack(stack.getItem(), stack.stackSize, forcedMetas.get(stack.getItem()));
	
		try{
			AMGuiHelper.DrawItemAtXY(stack, sx, sy, this.zLevel);
			RenderHelper.disableStandardItemLighting();
		}catch (Throwable t){
			forcedMetas.put(stack.getItem(), 0);
		}
	
		if (mousex > sx && mousex < sx + 16){
			if (mousey > sy && mousey < sy + 16){
				stackTip = stack;
				tipX = mousex;
				tipY = mousey;
			}
		}
	}

	
//
//	protected static RenderItem itemRenderer = Minecraft.getMinecraft().getRenderItem();
//	Minecraft mc;
//	String entryName;
//
//	private final HashMap<Item, Integer> forcedMetas;
//
//
//	int page = 0;
//	int numPages = 0;
//
//	int xSize = 360;
//	int ySize = 256;
//
//	int maxLines = 17;
//
//	int lineWidth = 140;
//
//	GuiButtonCompendiumNext nextPage;
//	GuiButtonCompendiumNext prevPage;
//
//	GuiButtonCompendiumTab backToIndex;
//
//	GuiSpellImageButton updateButton;
//
//	ItemStack stackTip;
//	int tipX, tipY;
//
//	CompendiumEntry entry;
//	ItemStack[] relatedEntries;
//	ArrayList<String> lines;
//	float framecount = 0;
//
//	//======================================================
//	// Recipe Rendering Variables
//	//======================================================
//	ItemStack entryItem;
//	int recipeWidth;
//	int recipeHeight;
//	Object[] craftingComponents;
//	Block entryBlock;
//	Item entryItem;
//	int entryMeta;
//	ArrayList<ItemStack> modifiers;
//	//======================================================
//
//	//======================================================
//	// Entity Rendering Variables
//	//======================================================
//	Entity entryEntity;
//	int curRotationH = 0;
//	int lastMouseX = 0;
//	boolean isDragging = false;
//	//======================================================
//
//	//======================================================
//	// Multiblock Rendering Variables
//	//======================================================
//	MultiblockStructureDefinition entryMultiblock;
//	GuiBlockAccess blockAccess;
//	GuiButtonCompendiumNext nextLayer;
//	GuiButtonCompendiumNext prevLayer;
//	GuiButtonVariableDims pauseCycling;
//	int maxLayers = 0;
//	int curLayer = -1;
//	//======================================================
//
//	//======================================================
//	// Ritual Entry Vars
//	//======================================================
//	IRitualInteraction ritualController;
//	//======================================================
//
//	public GuiArcaneCompendium(Block block){
//		this(block.getUnlocalizedName().replace("arsmagica2:", "").replace("tile.", ""));
//		this.entryBlock = block;
//		this.entryMeta = -1;
//		entryItem = new ItemStack(this.entryBlock);
//
//		getAndAnalyzeRecipe();
//	}
//
//	public GuiArcaneCompendium(Item item){
//		this(item.getUnlocalizedName().replace("item.", "").replace("arsmagica2:", ""));
//		this.entryItem = item;
//		this.entryMeta = -1;
//		entryItem = new ItemStack(this.entryItem);
//
//		getAndAnalyzeRecipe();
//	}
//
//	public GuiArcaneCompendium(String entryID, IBlockState state){
//		this(entryID);
//		this.entryBlock = state.getBlock();
//		this.entryMeta = entryBlock.getMetaFromState(state);
//		entryItem = new ItemStack(this.entryBlock, 1, entryMeta);
//
//		getAndAnalyzeRecipe();
//	}
//
//	public GuiArcaneCompendium(String entryID, Item item, int meta){
//		this(entryID);
//		this.entryItem = item;
//		this.entryMeta = meta;
//		entryItem = new ItemStack(this.entryItem, 1, meta);
//
//		getAndAnalyzeRecipe();
//	}
//
//	public GuiArcaneCompendium(Entity entity){
//		this(EntityList.getEntityString(entity).replace("arsmagica2.", ""));
//		this.entryEntity = entity;
//	}
//
//	public GuiArcaneCompendium(MultiblockStructureDefinition multi, TileEntity controllingTileEntity){
//		this(multi.getId());
//		this.entryMultiblock = multi;
//		this.blockAccess = new GuiBlockAccess();
//		this.blockAccess.setControllingTileEntity(controllingTileEntity);
//	}
//
//	public GuiArcaneCompendium(String id, MultiblockStructureDefinition multi, IRitualInteraction ritualController){
//		this(id);
//		this.entryMultiblock = multi;
//		this.blockAccess = new GuiBlockAccess();
//
//		this.ritualController = ritualController;
//		setupRitualPage();
//
//	}
//
//	private void setupRitualPage(){
//		this.blockAccess.setControllingTileEntity(null);
//		for (ItemStack stack : ritualController.getReagents()){
//			this.modifiers.add(stack);
//		}
//		ItemStack[] temp = new ItemStack[relatedEntries.length + 1];
//		for (int i = 0; i < relatedEntries.length; ++i){
//			temp[i] = relatedEntries[i];
//		}
////		temp[temp.length - 1] = new ItemStack(ItemDefs.spell_component, 1, SkillManager.instance.getShiftedPartID((ISkillTreeEntry)ritualController));
//		relatedEntries = temp;
//	}
//
//	public GuiArcaneCompendium(CompendiumBreadcrumb breadcrumb){
//		this(breadcrumb.entryName);
//		this.page = breadcrumb.page;
//		if (breadcrumb.refData.length == 3){
//			if (breadcrumb.refData[0] instanceof MultiblockStructureDefinition){
//				this.entryMultiblock = (MultiblockStructureDefinition)breadcrumb.refData[0];
//				this.blockAccess = new GuiBlockAccess();
//				this.blockAccess.setControllingTileEntity((TileEntity)breadcrumb.refData[1]);
//
//				this.ritualController = (IRitualInteraction)breadcrumb.refData[2];
//				setupRitualPage();
//			}
//		}else if (breadcrumb.refData.length == 2){
//			if (breadcrumb.refData[0] instanceof Block){
//				this.entryBlock = (Block)breadcrumb.refData[0];
//				this.entryMeta = (Integer)breadcrumb.refData[1];
//				entryItem = new ItemStack(this.entryBlock, 1, this.entryMeta);
//
//				getAndAnalyzeRecipe();
//			}else if (breadcrumb.refData[0] instanceof Item){
//				this.entryItem = (Item)breadcrumb.refData[0];
//				this.entryMeta = (Integer)breadcrumb.refData[1];
//				entryItem = new ItemStack(this.entryItem, 1, this.entryMeta);
//
//				getAndAnalyzeRecipe();
//			}else if (breadcrumb.refData[0] instanceof MultiblockStructureDefinition){
//				this.entryMultiblock = (MultiblockStructureDefinition)breadcrumb.refData[0];
//				this.blockAccess = new GuiBlockAccess();
//				this.blockAccess.setControllingTileEntity((TileEntity)breadcrumb.refData[1]);
//			}
//		}else if (breadcrumb.refData.length == 1){
//			if (breadcrumb.refData[0] instanceof Entity){
//				this.entryEntity = (Entity)breadcrumb.refData[0];
//			}
//		}
//	}
//
	private void getAndAnalyzeRecipe(){
		if (entryItem == null) return;

//		if (entryItem.getItem() == ItemDefs.essence || entryItem.getItem() == ItemDefs.deficitCrystal ){
//			RecipeArsMagica essenceRecipe = RecipesEssenceRefiner.essenceRefinement().recipeFor(entryItem);
//			if (essenceRecipe != null){
//				craftingComponents = essenceRecipe.getRecipeItems();
//				recipeHeight = 2;
//			}else{
//				craftingComponents = null;
//			}
//		}
		else if (entryItem.getItem() instanceof ItemSpellComponent){
			if (entrySkill == null) return;
			AbstractSpellPart part = ArsMagicaAPI.getSpellRegistry().getValue(entrySkill.getRegistryName());
			if (part == null) return;
			ArrayList<Object> recipe = new ArrayList<Object>();

			if (part instanceof AbstractSpellPart){
				Object[] recipeItems = ((AbstractSpellPart)part).getRecipe();
				SpellRecipeItemsEvent event = new SpellRecipeItemsEvent(entrySkill.getID(), recipeItems);
				MinecraftForge.EVENT_BUS.post(event);
				recipeItems = event.recipeItems;

				if (recipeItems != null){
					for (int i = 0; i < recipeItems.length; ++i){
						Object o = recipeItems[i];
						if (o instanceof ItemStack){
							recipe.add(o);
						}else if (o instanceof Item){
							recipe.add(new ItemStack((Item)o));
						}else if (o instanceof Block){
							recipe.add(new ItemStack((Block)o));
						}else if (o instanceof String){
//							if (((String)o).startsWith("P:")){
//								//potion
//								String s = ((String)o).substring(2);
//								int pfx = SpellRecipeManager.parsePotionMeta(s);
//								recipe.add(new ItemStack(Items.potionitem, 1, pfx));
//							}else 
							if (((String)o).startsWith("E:")){
								//essence
								String s = ((String)o);
								try{
									int[] types = RecipeUtils.ParseEssenceIDs(s);
									int type = 0;
									for (int t : types)
										type |= t;
									int amount = (Integer)recipeItems[++i];
									recipe.add(new ItemStack(ItemDefs.etherium, amount, type));
								}catch (Throwable t){
									continue;
								}
							}
							else {
								recipe.add(OreDictionary.getOres((String)o));
							}
						}
					}
				}
			}

			craftingComponents = recipe.toArray();

		}else{
			IRecipe recipe = RecipeUtils.getRecipeFor(entryItem);

			if (recipe != null){
				entryItem = recipe.getRecipeOutput();

				if (recipe instanceof ShapedRecipes){
					recipeWidth = ((ShapedRecipes)recipe).recipeWidth;
					recipeHeight = ((ShapedRecipes)recipe).recipeHeight;
					craftingComponents = ((ShapedRecipes)recipe).recipeItems;
				}else if (recipe instanceof ShapedOreRecipe){
					recipeWidth = ReflectionHelper.getPrivateValue(ShapedOreRecipe.class, ((ShapedOreRecipe)recipe), "width");
					recipeHeight = ReflectionHelper.getPrivateValue(ShapedOreRecipe.class, ((ShapedOreRecipe)recipe), "height");

					craftingComponents = ((ShapedOreRecipe)recipe).getInput();
				}else if (recipe instanceof ShapelessRecipes){
					recipeWidth = ((ShapelessRecipes)recipe).getRecipeSize();
					recipeHeight = -1;

					craftingComponents = ((ShapelessRecipes)recipe).recipeItems.toArray();
				}else if (recipe instanceof ShapelessOreRecipe){
					recipeWidth = ((ShapelessOreRecipe)recipe).getRecipeSize();
					recipeHeight = -1;

					craftingComponents = ((ShapelessOreRecipe)recipe).getInput().toArray();
				}else{
					craftingComponents = null;
				}
			}else{
				craftingComponents = null;
			}
		}
	}
//
//	public GuiArcaneCompendium(String entryName){
//		mc = Minecraft.getMinecraft();
//		entry = ArcaneCompendium.instance.getEntry(entryName);
//
//		this.entryName = entryName;
//
//		forcedMetas = new HashMap<Item, Integer>();
//
//		if (entry != null){
//			lines = splitStringToLines(Minecraft.getMinecraft().fontRendererObj, entry.getDescription(entryName), lineWidth, maxLines);
//			numPages = lines.size() - 1;
//			entry.setIsNew(false);
//		}else{
//			lines = new ArrayList<String>();
//			numPages = 0;
//			relatedEntries = new ItemStack[0];
//			modifiers = new ArrayList<ItemStack>();
//			return;
//		}
//
//		CompendiumEntry[] childEntries = entry.getRelatedItems();
//		relatedEntries = new ItemStack[childEntries.length];
//
//		String name = "";
//		int meta = -1;
//
//		for (int i = 0; i < childEntries.length; ++i){
//			if (childEntries[i].getID().indexOf('@') == -1){
//				name = childEntries[i].getID();
//			}else{
//				String[] split = childEntries[i].getID().split("@");
//				name = split[0];
//				try{
//					meta = Integer.parseInt(split[1]);
//				}catch (Throwable t){
//					meta = -1;
//				}
//			}
//			relatedEntries[i] = childEntries[i].getRepresentItemStack(name, meta);
//		}
//
//		modifiers = new ArrayList<ItemStack>();
//		SpellModifiers[] modifiedBy = new SpellModifiers[0];
//
//		if (entry instanceof CompendiumEntrySpellComponent){
//			modifiedBy = ((CompendiumEntrySpellComponent)entry).getModifiedBy();
//		}else if (entry instanceof CompendiumEntrySpellShape){
//			modifiedBy = ((CompendiumEntrySpellShape)entry).getModifiedBy();
//		}else if (entry instanceof CompendiumEntrySpellModifier){
//			modifiedBy = ((CompendiumEntrySpellModifier)entry).getModifies();
//		}
//
//		if (modifiedBy == null)
//			modifiedBy = new SpellModifiers[0];
//
//		if (modifiedBy.length > 0){
//			for (SpellData<IModifier> spelldata : SpellRegistry.getModifierMap().values()){
//				IModifier modifier = spelldata.part;
//				if (modifier != null){
//					for (SpellModifiers mod : modifiedBy){
//						if (entry instanceof CompendiumEntrySpellModifier){
//							if (modifier.getAspectsModified().size() == 1 && modifier.getAspectsModified().contains(mod)){
//								modifiers.add(new ItemStack(ItemDefs.spell_component, 1, ItemSpellComponent.getIdFor(modifier)));
//								break;
//							}
//						}else{
//							if (modifier.getAspectsModified().contains(mod)){
//								modifiers.add(new ItemStack(ItemDefs.spell_component, 1, ItemSpellComponent.getIdFor(modifier)));
//								break;
//							}
//						}
//					}
//				}
//			}
//		}
//	}
//
	@Override
	public void initGui(){
		super.initGui();

		int l = (width - xSize) / 2;
		int i1 = (height - ySize) / 2;

		prevPage = new GuiButtonCompendiumNext(0, l + 35, i1 + ySize - 25, false);
		nextPage = new GuiButtonCompendiumNext(1, l + 315, i1 + ySize - 25, true);

		backToIndex = new GuiButtonCompendiumTab(2, l - 10, i1 + 20, I18n.translateToLocal("am2.gui.back"), "back", null);
		backToIndex.setActive(true);

		prevLayer = new GuiButtonCompendiumNext(3, l + 180, i1 + 19, false);
		nextLayer = new GuiButtonCompendiumNext(4, l + 305, i1 + 19, true);
		pauseCycling = new GuiButtonVariableDims(5, l + 285, i1 + 190, AMGuiHelper.instance.runCompendiumTicker ? I18n.translateToLocal("am2.gui.pause") : I18n.translateToLocal("am2.gui.cycle")).setDimensions(40, 20);

		if (entryMultiblock != null){
			prevLayer.visible = true;
			nextLayer.visible = true;
			pauseCycling.visible = true;
			maxLayers = entryMultiblock.getHeight();
		}else{
			prevLayer.visible = false;
			nextLayer.visible = false;
			pauseCycling.visible = false;
		}

		this.buttonList.add(nextPage);
		this.buttonList.add(prevPage);
		this.buttonList.add(prevLayer);
		this.buttonList.add(nextLayer);
		this.buttonList.add(backToIndex);
		this.buttonList.add(pauseCycling);
	}
//
//	@Override
//	public boolean doesGuiPauseGame(){
//		return false;
//	}
//
//	@Override
//	protected void mouseClicked(int par1, int par2, int par3) throws IOException{
//		if (stackTip != null){
//			GuiArcaneCompendium newGuiToDisplay = null;
//			if (stackTip.getItem() instanceof ItemBlock){
//				ItemBlock item = (ItemBlock)stackTip.getItem();
//				Block block = item.block;
//
//				String name = block.getUnlocalizedName().replace("arsmagica2:", "").replace("tile.", "");
//				String metaname = name + "@" + stackTip.getItemDamage();
//				String searchName = metaname;
//				CompendiumEntry entry = ArcaneCompendium.instance.getEntry(metaname);
//				if (entry == null){
//					searchName = name;
//					entry = ArcaneCompendium.instance.getEntry(searchName);
//				}
//				if (entry != null){
//					newGuiToDisplay = entry.getCompendiumGui(searchName);
//				}
//			}
////			else if (stackTip.getItem() == ItemDefs.spell_component){
////				String name = SkillManager.instance.getSkillName(SkillManager.instance.getSkill(stackTip.getItemDamage()));
////				CompendiumEntry entry = ArcaneCompendium.instance.getEntry(name);
////				if (entry != null){
////					newGuiToDisplay = new GuiArcaneCompendium(name, stackTip.getItem(), stackTip.getItemDamage());
////				}
////			}
//			else{
//				String name = stackTip.getItem().getUnlocalizedName().replace("item.", "").replace("arsmagica2:", "");
//				String metaname = name + "@" + stackTip.getItemDamage();
//				String searchName = metaname;
//				CompendiumEntry entry = ArcaneCompendium.instance.getEntry(metaname);
//				if (entry == null){
//					searchName = name;
//					entry = ArcaneCompendium.instance.getEntry(searchName);
//				}
//				if (entry != null){
//					newGuiToDisplay = entry.getCompendiumGui(searchName);
//				}
//			}
//			if (newGuiToDisplay != null){
//
//				storeBreadcrumb();
//
//				Minecraft.getMinecraft().displayGuiScreen(newGuiToDisplay);
//			}
//			return;
//		}else if (entryEntity != null){
//			isDragging = true;
//			lastMouseX = par1;
//		}
//		super.mouseClicked(par1, par2, par3);
//	}
//
//	private void storeBreadcrumb(){
//		if (this.ritualController != null){
//			AMGuiHelper.instance.pushCompendiumBreadcrumb(this.entryName, this.page, CompendiumBreadcrumb.TYPE_ENTRY, this.entryMultiblock, this.blockAccess.getTileEntity(BlockPos.ORIGIN), this.ritualController);
//		}else if (this.entryItem != null){
//			AMGuiHelper.instance.pushCompendiumBreadcrumb(this.entryName, this.page, CompendiumBreadcrumb.TYPE_ENTRY, this.entryItem, this.entryMeta);
//		}else if (this.entryBlock != null){
//			AMGuiHelper.instance.pushCompendiumBreadcrumb(this.entryName, this.page, CompendiumBreadcrumb.TYPE_ENTRY, this.entryBlock, this.entryMeta);
//		}else if (this.entryEntity != null){
//			AMGuiHelper.instance.pushCompendiumBreadcrumb(this.entryName, this.page, CompendiumBreadcrumb.TYPE_ENTRY, this.entryEntity);
//		}else if (this.entryMultiblock != null){
//			AMGuiHelper.instance.pushCompendiumBreadcrumb(this.entryName, this.page, CompendiumBreadcrumb.TYPE_ENTRY, this.entryMultiblock, this.blockAccess.getTileEntity(BlockPos.ORIGIN));
//		}
//	}
//
//	@Override
//	protected void mouseReleased(int mouseX, int mouseY, int state) {
//		if (isDragging){
//			if (state == 1){
//				isDragging = false;
//			}
//		}
//		super.mouseReleased(mouseX, mouseY, state);
//	}
//	
//	
//	@Override
//	protected void mouseClickMove(int par1, int par2, int par3, long par4){
//		if (isDragging){
//			curRotationH -= (lastMouseX - par1);
//
//			lastMouseX = par1;
//		}
//		super.mouseClickMove(par1, par2, par3, par4);
//	}
//
	@Override
	protected void actionPerformed(GuiButton par1GuiButton){
		switch (par1GuiButton.id){
		case 0: //prev page
			if (page > 0) page--;
			break;
		case 1: //next page
			if (page < numPages) page++;
			break;
		case 2:
//			CompendiumBreadcrumb prevEntry = AMGuiHelper.instance.popCompendiumBreadcrumb();
//			if (prevEntry != null){
//				if (prevEntry.entryType == prevEntry.TYPE_ENTRY)
//					Minecraft.getMinecraft().displayGuiScreen(new GuiArcaneCompendium(prevEntry));
//				else
//					Minecraft.getMinecraft().displayGuiScreen(new GuiCompendiumIndex(prevEntry));
//			}else{
			Minecraft.getMinecraft().displayGuiScreen(new GuiCompendiumIndex());
//			}
			break;
		case 3:
			curLayer--;
			if (curLayer < -1){
				curLayer = maxLayers;
			}
			break;
		case 4:
			curLayer++;
			if (curLayer > maxLayers){
				curLayer = -1;
			}
			break;
		case 5:
			AMGuiHelper.instance.runCompendiumTicker = !AMGuiHelper.instance.runCompendiumTicker;
			pauseCycling.displayString = AMGuiHelper.instance.runCompendiumTicker ? I18n.translateToLocal("am2.gui.pause") : I18n.translateToLocal("am2.gui.cycle");
			break;
		}
	}
//
//	@Override
//	public void drawScreen(int par1, int par2, float par3){
//		framecount += 0.5f;
//
//		framecount %= 360;
//
//		int l = (width - xSize) / 2;
//		int i1 = (height - ySize) / 2;
//
//		stackTip = null;
//
//		drawLeftPage(l, i1);
//		drawRightPage(l, i1, par1, par2);
//
//		if (this.page == 0)
//			prevPage.visible = false;
//		else
//			prevPage.visible = true;
//
//
//		if (this.page == numPages)
//			nextPage.visible = false;
//		else
//			nextPage.visible = true;
//
//		RenderHelper.disableStandardItemLighting();
//
//		GL11.glPushMatrix();
//		GL11.glTranslatef(0, 0, -1);
//
//		this.drawDefaultBackground();
//
//		mc.renderEngine.bindTexture(background);
//		GL11.glColor3f(1.0f, 1.0f, 1.0f);
//		this.drawTexturedModalRect_Classic(l, i1, 0, 0, xSize, ySize, 256, 240);
//
//		drawRightPageExtras(l, i1);
//
//		GL11.glPopMatrix();
//
//		RenderHelper.enableStandardItemLighting();
//
//		super.drawScreen(par1, par2, par3);
//
//		if (stackTip != null){
//			renderItemToolTip(stackTip, tipX, tipY);
//		}
//
//		if (this.entryMultiblock != null){
//			fontRendererObj.drawString(I18n.translateToLocal("am2.gui.mbb"), l + 190, i1 + 195, 0x000000);
//		}
//	}
//
//	@Override
//	protected void keyTyped(char par1, int par2) throws IOException{
//		if (par2 == 1){
//			storeBreadcrumb();
//			onGuiClosed();
//		}
//		super.keyTyped(par1, par2);
//	}
//
//	private void drawLeftPage(int l, int i1){
//
//		if (entry == null) return;
//
//		int y_start_title = i1 + 50;
//		int x_start_title = l + 100 - (fontRendererObj.getStringWidth(entry.getName(entryName)) / 2);
//
//		int x_start_line = l + 35;
//		int y_start_line = page == 0 ? i1 + 65 : i1 + 50;
//
//		if (page > numPages) page = numPages;
//
//		if (entry != null){
//			if (page == 0)
//				fontRendererObj.drawString(entry.getName(entryName), x_start_title, y_start_title, 0x000000);
//			AMGuiHelper.drawCompendiumText(lines.get(page), x_start_line, y_start_line, lineWidth, 0x000000, fontRendererObj);
//		}
//	}
//
//	private void drawRightPage(int l, int i1, int mousex, int mousey){
//		int cx = l + 250;
//		int cy = i1 + 120 - 6;
//
//		//block/item
//		if (entryBlock != null || entryItem != null){
//			drawRightPage_Block_Item(cx, cy, mousex, mousey);
//		}else if (entryEntity != null){
//			drawRightPage_Entity(cx, cy);
//		}else if (entryMultiblock != null){
//			drawRightPage_Multiblock(cx, cy, mousex, mousey);
//		}
//
//		drawRelatedItems(cx, cy, mousex, mousey);
//		if (modifiers.size() > 0)
//			drawModifiers(cx, entryMultiblock == null ? cy : cy + 20, mousex, mousey);
//	}
//
	private void drawRightPageExtras(int l, int i1){
		int cx = l + 256 - 6;
		int cy = i1 + 120 - 6;

		mc.renderEngine.bindTexture(extras);
		zLevel++;
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		this.drawTexturedModalRect_Classic(l + 305, i1 + 15, 112, 145, 60, 40, 40, 40);
		this.drawTexturedModalRect_Classic(l + 180, i1 + 200, 112, 175, 60, 40, 40, 40);
		GL11.glDisable(GL11.GL_BLEND);
		//block/item
		if (entryBlock != null || entryItem != null){
			drawRightPageExtras_Block_Item(cx, cy);
		}else if (entryMultiblock == null){
			drawRightPageExtras_Generic(cx, cy);
		}
		zLevel--;
	}
//
	private void drawRelatedItems(int cx, int cy, int mouseX, int mouseY){
		if (entry.getRelatedItems().length == 0)
			return;

		int length = Math.min(entry.getRelatedItems().length, 5);

		int step = 18;
		int x = cx - (length * step / 2) + step / 2;
		int y = cy + ySize / 2 - 32;

		y += 16;

		int count = 0;

		for (int i = 0; i < entry.getRelatedItems().length && count < 5; i++){
			ItemStack stack = ArcaneCompendium.getCompendium().get(entry.getRelatedItems()[i]).getRepresentStack();
			if (stack != null){
				AMGuiHelper.DrawItemAtXY(stack, x, y, this.zLevel);
				if (mouseX > x && mouseX < x + 16){
					if (mouseY > y && mouseY < y + 16){
						stackTip = stack;
						tipX = mouseX;
						tipY = mouseY;
					}
				}
				x += step;
				count++;
			}
		}

		y -= 16;

		if (count > 0){
			String s = I18n.translateToLocal("am2.gui.relatedItems");
			fontRendererObj.drawString(s, cx - xSize / 6, y, 0x00000);
		}
	}

	private void drawModifiers(int cx, int cy, int mouseX, int mouseY){

		int step = 18;

		String label = "";
		
		if (this.entryMultiblock != null)
			label = I18n.translateToLocal("am2.gui.ritual");
		else if (entry instanceof CompendiumEntrySpellModifier)
			label = I18n.translateToLocal("am2.gui.modifies");
		else
			label = I18n.translateToLocal("am2.gui.modifiedBy");

		int x = cx - fontRendererObj.getStringWidth(label) / 2;
		int y = cy - ySize / 2 + 28;

		fontRendererObj.drawString(label, x, y, 0x000000);
		Minecraft.getMinecraft().renderEngine.bindTexture(LOCATION_BLOCKS_TEXTURE);

		y += 12;
		x = cx - (int)(modifiers.size() / 2.0f * step);

		for (ItemStack mod : modifiers){
			if (mod != null){
				AMGuiHelper.DrawItemAtXY(mod, x, y, this.zLevel);
				if (mouseX > x && mouseX < x + 16){
					if (mouseY > y && mouseY < y + 16){
						stackTip = mod;
						tipX = mouseX;
						tipY = mouseY;
					}
				}
			}
			x += step;
		}
	}

	private void drawRightPage_Entity(int cx, int cy){

		double ex = cx;
		double ey = cy;

		GL11.glPushMatrix();
		GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		GL11.glTranslatef((float)(ex - 2), (float)(ey + 20), -3.0F + Minecraft.getMinecraft().getRenderItem().zLevel);
		GL11.glScalef(10.0F, 10.0F, 10.0F);
		GL11.glTranslatef(1.0F, 6.5F, 1.0F);
		GL11.glScalef(6.0F, 6.0F, -1.0F);
		GL11.glRotatef(210.0F, 1.0F, 0.0F, 0.0F);
		GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
		GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);

		renderEntityIntoUI();
		GL11.glPopMatrix();

		int i1 = (height - ySize) / 2;

		String renderString = "Click and drag to rotate";
		fontRendererObj.drawString(renderString, cx - fontRendererObj.getStringWidth(renderString) / 2, i1 + 20, 0x000000);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void renderEntityIntoUI(){
		Render render = (Render)Minecraft.getMinecraft().getRenderManager().entityRenderMap.get(entryEntity.getClass());
		if (render != null){
			GL11.glPushMatrix();
//			if (entryEntity instanceof IArsMagicaBoss){
//				float scaleFactorX = (1 / entryEntity.width);
//				float scaleFactorY = (2 / entryEntity.height);
//				GL11.glScalef(scaleFactorX, scaleFactorY, scaleFactorX);
//			}
//			else if (entryEntity instanceof EntityFlicker){
//				GL11.glTranslatef(0, 1.3f, 0);
//			}
			GL11.glRotatef(curRotationH, 0, 1, 0);

			//entity, x, y, z, yaw, partialtick
			render.doRender(entryEntity, 0, 0, 0, 90, 0);

			GL11.glPopMatrix();
		}
	}
//
//	private void drawRightPage_Block_Item(int cx, int cy, int mousex, int mousey){
//
//		RenderHelper.disableStandardItemLighting();
//
//		int l = (width - xSize) / 2;
//		int i1 = (height - ySize) / 2;
//
//		if (craftingComponents == null){
//			AMGuiHelper.instance.DrawItemAtXY(entryItem, cx, cy, this.zLevel);
//			if (mousex > cx && mousex < cx + 16){
//				if (mousey > cy && mousey < cy + 16){
//					stackTip = this.entryItem;
//					tipX = mousex;
//					tipY = mousey;
//				}
//			}
//		}else{
//			RenderRecipe(cx, cy, mousex, mousey);
//		}
//
//		if (this.entryItem.getItem() instanceof ItemSpellComponent){
//			TextureAtlasSprite icon = SpellIconManager.INSTANCE.getSprite(entryName);
//			mc.renderEngine.bindTexture(items);
//			GL11.glColor4f(1, 1, 1, 1);
//			DrawIconAtXY(icon, cx, cy, 16, 16);
//		}
//
//		RenderHelper.enableStandardItemLighting();
//	}
//
	private void drawRightPageExtras_Block_Item(int cx, int cy){
		if (craftingComponents == null){
			GL11.glPushMatrix();

			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			this.drawTexturedModalRect_Classic(cx - 77, cy - 68, 0, 101, 150, 150, 100, 147);
			GL11.glDisable(GL11.GL_BLEND);
			GL11.glPopMatrix();
		}else{

			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
//			if (this.entryItem.getItem() == ItemDefs.essence || this.entryItem.getItem() == ItemDefs.deficitCrystal){
//				this.drawTexturedModalRect_Classic(cx - 43, cy - 45, 367, 0, 105, 105, 70, 105);
//			}else
			if (this.entryItem.getItem() == ItemDefs.spell_component){
				//intentionally do nothing
			}else{
				this.drawTexturedModalRect_Classic(cx - 43, cy - 43, 0, 0, 100, 100, 67, 95);
			}
			GL11.glDisable(GL11.GL_BLEND);
		}
	}

	private void renderItemToolTip(ItemStack stack, int x, int y){
		try{
			List<String> list = stack.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);

			CompendiumEntry entry;
			if (stack.getItem() instanceof ItemBlock){
				ItemBlock item = (ItemBlock)stack.getItem();
				Block block = item.block;
				String name = block.getUnlocalizedName().replace("arsmagica2:", "").replace("tile.", "");
				String metaname = name + "@" + stack.getItemDamage();
				entry = ArcaneCompendium.getCompendium().get(metaname);
				if (entry == null)
					entry = ArcaneCompendium.getCompendium().get(name);
			}else{
				if (stack.getItem() == ItemDefs.spell_component){
					list.clear();
					Skill skill = ArsMagicaAPI.getSkillRegistry().getObjectById(stack.getItemDamage());
					if (skill == null)
						return;
					list.add(skill.getName());
					entry = ArcaneCompendium.getCompendium().get(skill.getID());
				}else if (stack.getItem() == ItemDefs.etherium){
					list.clear();
					list.add(stack.stackSize + " " + I18n.translateToLocal("item.etherium.name"));
					ArrayList<String> subList = new ArrayList<>();
					for (PowerTypes type : PowerTypes.all()) {
						if ((stack.getItemDamage() & type.ID()) == type.ID()) {
							subList.add(type.getChatColor() + I18n.translateToLocal("etherium." + type.name() + ".name"));
						}
					}
					if (subList.size() == PowerTypes.all().size()) {
						list.add(TextFormatting.GRAY.toString() + I18n.translateToLocal("etherium.any.name"));
					} else {
						list.addAll(subList);
					}
					entry = ArcaneCompendium.getCompendium().get("etherium");
				}else{
					String name = stack.getItem().getUnlocalizedName().replace("item.", "").replace("arsmagica2:", "");
					String metaname = name + "@" + stack.getItemDamage();
					entry = ArcaneCompendium.getCompendium().get(metaname);
					if (entry == null)
						entry = ArcaneCompendium.getCompendium().get(name);
				}
			}

			for (int k = 0; k < list.size(); ++k){
				if (k == 0){
					if (entry != null){
						list.set(k, "\u00a72" + (String)list.get(k));
					}else{
						list.set(k, stack.getRarity().rarityColor.toString() + (String)list.get(k));
					}
				}else{
					list.set(k, TextFormatting.GRAY.toString() + (String)list.get(k));
				}
			}

			//split out multiline entries (only entry 0 in this case)
			if (((String)list.get(0)).indexOf('\n') != -1){
				String s = ((String)list.get(0));
				String colorPrefix = "";
				list.remove(0);
				if (entry != null){
					colorPrefix = "\u00a72";
				}else{
					colorPrefix = stack.getRarity().rarityColor.toString();
				}
				String[] split = s.split("\n");
				for (int i = split.length - 1; i >= 0; --i){
					list.add(0, colorPrefix + split[i]);
				}
			}

			FontRenderer font = stack.getItem().getFontRenderer(stack);
			drawHoveringText(list, x, y, (font == null ? this.fontRendererObj : font));
		}catch (Throwable t){

		}
	}

	protected void drawHoveringText(List<String> par1List, int par2, int par3, FontRenderer font){
		if (!par1List.isEmpty()){
			GL11.glDisable(GL12.GL_RESCALE_NORMAL);
			RenderHelper.disableStandardItemLighting();
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			int k = 0;
			Iterator<String> iterator = par1List.iterator();

			while (iterator.hasNext()){
				String s = (String)iterator.next();
				int l = font.getStringWidth(s);

				if (l > k){
					k = l;
				}
			}

			int i1 = par2 + 12;
			int j1 = par3 - 12;
			int k1 = 8;

			if (par1List.size() > 1){
				k1 += 2 + (par1List.size() - 1) * 10;
			}

			if (i1 + k > this.width){
				i1 -= 28 + k;
			}

			if (j1 + k1 + 6 > this.height){
				j1 = this.height - k1 - 6;
			}

			this.zLevel = 300.0F;
			Minecraft.getMinecraft().getRenderItem().zLevel = 300.0F;
			int l1 = -267386864;
			this.drawGradientRect(i1 - 3, j1 - 4, i1 + k + 3, j1 - 3, l1, l1);
			this.drawGradientRect(i1 - 3, j1 + k1 + 3, i1 + k + 3, j1 + k1 + 4, l1, l1);
			this.drawGradientRect(i1 - 3, j1 - 3, i1 + k + 3, j1 + k1 + 3, l1, l1);
			this.drawGradientRect(i1 - 4, j1 - 3, i1 - 3, j1 + k1 + 3, l1, l1);
			this.drawGradientRect(i1 + k + 3, j1 - 3, i1 + k + 4, j1 + k1 + 3, l1, l1);
			int i2 = 1347420415;
			int j2 = (i2 & 16711422) >> 1 | i2 & -16777216;
			this.drawGradientRect(i1 - 3, j1 - 3 + 1, i1 - 3 + 1, j1 + k1 + 3 - 1, i2, j2);
			this.drawGradientRect(i1 + k + 2, j1 - 3 + 1, i1 + k + 3, j1 + k1 + 3 - 1, i2, j2);
			this.drawGradientRect(i1 - 3, j1 - 3, i1 + k + 3, j1 - 3 + 1, i2, i2);
			this.drawGradientRect(i1 - 3, j1 + k1 + 2, i1 + k + 3, j1 + k1 + 3, j2, j2);

			for (int k2 = 0; k2 < par1List.size(); ++k2){
				String s1 = (String)par1List.get(k2);
				font.drawStringWithShadow(s1, i1, j1, -1);

				if (k2 == 0){
					j1 += 2;
				}

				j1 += 10;
			}

			this.zLevel = 0.0F;
			Minecraft.getMinecraft().getRenderItem().zLevel = 0.0F;
			GL11.glEnable(GL11.GL_LIGHTING);
			GL11.glEnable(GL11.GL_DEPTH_TEST);
			RenderHelper.enableStandardItemLighting();
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
		}
	}

	private void drawRightPage_Multiblock(int cx, int cy, int mousex, int mousey){

		String label = String.format("%s: %s", I18n.translateToLocal("am2.gui.layer"), curLayer == -1 ? I18n.translateToLocal("am2.gui.all") : "" + curLayer);

		fontRendererObj.drawString(label, cx - fontRendererObj.getStringWidth(label) / 2, cy - 90, 0x000000);

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LIGHTING_BIT);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_LIGHTING);

		/*ArrayList<IBlockState> blox = entryMultiblock.getAllowedBlocksAt(entryMultiblock.new BlockPos(0, 0, 0));
		if (blox != null){
			renderBlock(Block.blocksList[blox.get(0).getID()], blox.get(0).getMeta(), cx, cy);
		}*/
		BlockPos pickedBlock = getPickedBlock(cx, cy, mousex, mousey);
		if (curLayer == -1){
			for (int i = entryMultiblock.getMinY(); i <= entryMultiblock.getMaxY(); ++i){
				int y = (i - entryMultiblock.getMinY());
				GL11.glTranslatef(0.0f, 0.0f, 20f * y);
				drawMultiblockLayer(cx, cy, i, pickedBlock, mousex, mousey);
			}
		}else{
			int i = entryMultiblock.getMinY() + curLayer;
			GL11.glTranslatef(0.0f, 0.0f, 20f * curLayer);
			drawMultiblockLayer(cx, cy, i, pickedBlock, mousex, mousey);
		}
		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private TreeMap<BlockPos, List<IBlockState>> getMultiblockLayer(int layer){
		TreeMap<BlockPos, List<IBlockState>> layerBlocksSorted = new TreeMap<>();

		for (MultiblockGroup mutex : entryMultiblock.getGroups()){
			HashMap<BlockPos, List<IBlockState>> layerBlocks = entryMultiblock.getStructureLayer(mutex, layer);
			for (BlockPos bc : layerBlocks.keySet()){
				if (mutex instanceof TypedMultiblockGroup) {
					TypedMultiblockGroup newGroup = (TypedMultiblockGroup) mutex;
					layerBlocksSorted.put(bc, newGroup.getState(bc));
				} else {
					layerBlocksSorted.put(bc, layerBlocks.get(bc));
				}
			}
		}

		return layerBlocksSorted;
	}

	private BlockPos getPickedBlock(int cx, int cy, int mousex, int mousey){
		BlockPos block = null;

		float step_x = 14f;
		float step_y = -16.0f;
		float step_z = 7f;

		cy -= step_y * entryMultiblock.getMinY() / 2;
		cy -= step_y * entryMultiblock.getMaxY() / 2;

		int start = curLayer == -1 ? entryMultiblock.getMinY() : entryMultiblock.getMinY() + curLayer;
		int end = curLayer == -1 ? entryMultiblock.getMaxY() : entryMultiblock.getMinY() + curLayer;

		for (int i = start; i <= end; ++i){
			TreeMap<BlockPos, List<IBlockState>> layerBlocksSorted = getMultiblockLayer(i);

			float px = cx - (step_x * (entryMultiblock.getWidth() / 2));
			float py = cy - (step_z * (entryMultiblock.getLength() / 2));
						
			for (BlockPos bc : layerBlocksSorted.keySet()){
				float x = px + ((bc.getX() - bc.getZ()) * step_x);
				float y = py + ((bc.getZ() + bc.getX()) * step_z) + (step_y * i);

				x += 20;
				y -= 10;

				if (mousex > x && mousex < x + 32){
					if (mousey > y && mousey < y + 32){
						block = bc;
					}
				}
			}
		}
		return block;
	}

	private void drawMultiblockLayer(int cx, int cy, int layer, BlockPos pickedBlock, int mousex, int mousey){
		TreeMap<BlockPos, List<IBlockState>> layerBlocksSorted = getMultiblockLayer(layer);
		float step_x = 14f;
		float step_y = -16.0f;
		float step_z = 7f;
		cy -= step_y * entryMultiblock.getMinX() / 2;
		cy -= step_y * entryMultiblock.getMaxY() / 2;

		float px = cx - (step_x * (entryMultiblock.getWidth() / 2));
		float py = cy - (step_z * (entryMultiblock.getLength() / 2));

		for (BlockPos bc : layerBlocksSorted.keySet()){
			//if (bc.getX() == 0 && bc.getY() == 0 && bc.getZ() == 0) continue;
			IBlockState bd = layerBlocksSorted.get(bc).get(AMGuiHelper.instance.getSlowTicker() % layerBlocksSorted.get(bc).size());
			float x = px + ((bc.getX() - bc.getZ()) * step_x);
			float y = py + ((bc.getZ() + bc.getX()) * step_z) + (step_y * layer);
			GL11.glPushMatrix();
			GL11.glTranslatef(0, 0, 15 * bc.getX());
			boolean picked = pickedBlock != null && bc.equals(pickedBlock);
			renderBlock(bd, x, y, bc.getX(), bc.getY(), bc.getZ(), picked);
			GL11.glPopMatrix();

			if (picked){
				ItemStack stack = new ItemStack(bd.getBlock(), 1, bd.getBlock().getMetaFromState(bd));
				if (stack.getItem() != null){
					stackTip = stack;
					tipX = mousex;
					tipY = mousey;
				}
			}
		}
	}

	private void drawRightPageExtras_Generic(int cx, int cy){

		mc.renderEngine.bindTexture(extras);

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		this.drawTexturedModalRect_Classic(cx - 77, cy - 68, 0, 101, 150, 150, 100, 147);
		GL11.glDisable(GL11.GL_BLEND);
	}
//
//	private void RenderRecipe(int cx, int cy, int mousex, int mousey){
//		int step = 32;
//		int sx = cx - step;
//		int sy = cy - step;
//
//		if (craftingComponents == null) return;
//
//		if (this.entryItem.getItem() == ItemDefs.essence
////				|| this.entryItem.getItem() == ItemDefs.deficitCrystal
//				){
//			renderCraftingComponent(0, cx, cy - 36, mousex, mousey);
//			renderCraftingComponent(1, cx - 30, cy - 2, mousex, mousey);
//			renderCraftingComponent(2, cx, cy - 2, mousex, mousey);
//			renderCraftingComponent(3, cx + 28, cy - 2, mousex, mousey);
//			renderCraftingComponent(4, cx, cy + 30, mousex, mousey);
//			return;
//		}
////		else if (this.entryItem.getItem() == ItemDefs.spell_component){
////			float angleStep = (360.0f / craftingComponents.length);
////			for (int i = 0; i < craftingComponents.length; ++i){
////				float angle = (float)(Math.toRadians((angleStep * i) + framecount % 360));
////				float nextangle = (float)(Math.toRadians((angleStep * ((i + 1) % craftingComponents.length)) + framecount % 360));
////				float dist = 45;
////				int x = (int)Math.round(cx - Math.cos(angle) * dist);
////				int y = (int)Math.round(cy - Math.sin(angle) * dist);
////				int nextx = (int)Math.round(cx - Math.cos(nextangle) * dist);
////				int nexty = (int)Math.round(cy - Math.sin(nextangle) * dist);
////				AMGuiHelper.line2d(x + 8, y + 8, cx + 8, cy + 8, zLevel, 0x0000DD);
////				AMGuiHelper.gradientline2d(x + 8, y + 8, nextx + 8, nexty + 8, zLevel, 0x0000DD, 0xDD00DD);
////				renderCraftingComponent(i, x, y, mousex, mousey);
////			}
////			return;
////		}
//
//		if (recipeHeight > 0){
//			for (int i = 0; i < recipeHeight; ++i){
//				for (int j = 0; j < recipeWidth; ++j){
//					int index = i * recipeWidth + j;
//
//					renderCraftingComponent(index, sx, sy, mousex, mousey);
//					sx += step;
//				}
//
//				sx = cx - step;
//				sy += step;
//			}
//		}else{
//			int col = 0;
//			int row = 0;
//			int widthSq = (int)Math.ceil(Math.sqrt(recipeWidth));
//
//			String label = "\247nShapeless";
//			fontRendererObj.drawString(label, cx - (int)(fontRendererObj.getStringWidth(label) / 2.5), cy - step * 3, 0x6600FF);
//
//			for (int i = 0; i < recipeWidth; ++i){
//				sx = cx - step + (step * col);
//				sy = cy - step + (step * row);
//				int index = (row * widthSq) + (col++);
//				if (col >= widthSq){
//					row++;
//					col = 0;
//				}
//
//				renderCraftingComponent(index, sx, sy, mousex, mousey);
//			}
//		}
//
//		sx = cx;
//		sy = cy - (int)(2.5 * step);
//
//		if (entryItem != null){
//			AMGuiHelper.instance.DrawItemAtXY(entryItem, sx, sy, this.zLevel);
//
//			if (entryItem.stackSize > 1)
//				fontRendererObj.drawString("x" + entryItem.stackSize, sx + 16, sy + 8, 0, false);
//
//			if (mousex > sx && mousex < sx + 16){
//				if (mousey > sy && mousey < sy + 16){
//					stackTip = this.entryItem;
//					tipX = mousex;
//					tipY = mousey;
//				}
//			}
//		}
//	}
//
//	private void renderCraftingComponent(int index, int sx, int sy, int mousex, int mousey){
//		Object craftingComponent = craftingComponents[index];
//
//		if (craftingComponent == null) return;
//
//		ItemStack stack = null;
//
//		if (craftingComponent instanceof ItemStack){
//			stack = (ItemStack)craftingComponent;
//		}else if (craftingComponent instanceof List){
//			if (((List)craftingComponent).size() == 0)
//				return;
//			int idx = new Random(AMGuiHelper.instance.getSlowTicker()).nextInt(((List)craftingComponent).size());
//			stack = ((ItemStack)((List)craftingComponent).get(idx)).copy();
//		}
//
//		List<ItemStack> oredict = OreDictionary.getOres(stack.getItem().getUnlocalizedName());
//		List<ItemStack> alternates = new ArrayList<ItemStack>();
//		alternates.addAll(oredict);
//
//		if (alternates.size() > 0){
//			alternates.add(stack);
//			stack = alternates.get(new Random(AMGuiHelper.instance.getSlowTicker()).nextInt(alternates.size()));
//		}
//
//		if (forcedMetas.containsKey(stack.getItem()))
//			stack = new ItemStack(stack.getItem(), stack.stackSize, forcedMetas.get(stack.getItem()));
//
//		try{
//			AMGuiHelper.instance.DrawItemAtXY(stack, sx, sy, this.zLevel);
//			RenderHelper.disableStandardItemLighting();
//		}catch (Throwable t){
//			forcedMetas.put(stack.getItem(), 0);
//		}
//
//		if (mousex > sx && mousex < sx + 16){
//			if (mousey > sy && mousey < sy + 16){
//				stackTip = stack;
//				tipX = mousex;
//				tipY = mousey;
//			}
//		}
//	}
//
	private void renderBlock(IBlockState state, float x, float y, int offsetX, int offsetY, int offsetZ, boolean picked){

		RenderHelper.disableStandardItemLighting();

		GL11.glPushMatrix();
		GL11.glTranslatef(x + 15, y + 3, 12.0F * offsetZ);
		GL11.glTranslatef(0, 0, 40);
		GL11.glScalef(20.0F, 20.0F, 20.0F);
		GL11.glTranslatef(1.0F, 0.5F, 1.0F);
		GL11.glScalef(1.0F, 1.0F, -1.0F);
		GL11.glRotatef(210.0F, 1.0F, 0.0F, 0.0F);
		GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
		GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
		this.blockAccess.setFakeBlockAndMeta(state);
		if (picked)
			mc.renderEngine.bindTexture(red);
		else
			mc.renderEngine.bindTexture(LOCATION_BLOCKS_TEXTURE);
		GL11.glEnable(GL11.GL_LIGHTING);
		if (state.getBlock() instanceof ITileEntityProvider)
			TileEntityRendererDispatcher.instance.renderTileEntityAt(((ITileEntityProvider)state.getBlock()).createNewTileEntity(Minecraft.getMinecraft().theWorld, state.getBlock().getMetaFromState(state)), 0, 0, 0, 0, 0);
		GL11.glDisable(GL11.GL_LIGHTING);
		Tessellator.getInstance().getBuffer().begin(7, DefaultVertexFormats.BLOCK);
		GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
		Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(state, new BlockPos(0, 0, 0), blockAccess , Tessellator.getInstance().getBuffer());
		Tessellator.getInstance().draw();
		
//		if (block.getRenderType() == BlocksCommonProxy.blockRenderID){
//			blockRenderer.useInventoryTint = false;
//			if (picked) GL11.glColor3f(1.0f, 0.0f, 0.0f);
//			else GL11.glColor3f(0.8f, 0.8f, 0.8f);
//			GL11.glRotatef(-90F, 0, 1.0f, 0);
//			GL11.glTranslatef(0, 0, -1);
//			this.blockRenderer.renderBlockAsItem(block, meta, 0);
//		}else{
//			Tessellator.instance.startDrawingQuads();
//			this.blockRenderer.renderBlockByRenderType(block, 0, 0, 0);
//			Tessellator.instance.draw();
//		}
		RenderHelper.disableStandardItemLighting();
		GL11.glPopMatrix();
	}
//
//	public static ArrayList<String> splitStringToLines(FontRenderer fontRenderer, String string, int lineWidth, int maxLines){
//		ArrayList<String> toReturn = new ArrayList<String>();
//		int numLines = 0;
//		int len = 0;
//		int pageCount = 0;
//		String[] words = string.replace("\n", " ").replace("\t", "").split(" ");
//		StringBuilder sb = new StringBuilder();
//		String curLine = "";
//		for (String s : words){
//			s = s.trim();
//			int wordWidth = fontRenderer.getStringWidth(s + " ");
//			if (s.equals(ArcaneCompendium.KEYWORD_NEWPAGE)){
//				sb.append(curLine);
//				curLine = "";
//				len = 0;
//				numLines++;
//				toReturn.add(sb.toString());
//				sb = new StringBuilder();
//				pageCount++;
//				numLines = 0;
//				continue;
//			}else if (s.equals(ArcaneCompendium.KEYWORD_NEWLINE)){
//				sb.append(curLine + "\n");
//				curLine = "";
//				len = 0;
//				numLines++;
//				if ((numLines == maxLines && pageCount > 0) || (pageCount == 0 && numLines == maxLines - 2)){
//					toReturn.add(sb.toString());
//					sb = new StringBuilder();
//					pageCount++;
//					numLines = 0;
//				}
//				continue;
//			}else if (s.equals(ArcaneCompendium.KEYWORD_DOUBLENEWLINE)){
//				sb.append(curLine + "\n");
//				curLine = "";
//				len = 0;
//				numLines++;
//				if ((numLines == maxLines && pageCount > 0) || (pageCount == 0 && numLines == maxLines - 2)){
//					toReturn.add(sb.toString());
//					sb = new StringBuilder();
//					pageCount++;
//					numLines = 0;
//				}
//				sb.append(curLine + "\n");
//				curLine = "";
//				len = 0;
//				numLines++;
//				if ((numLines == maxLines && pageCount > 0) || (pageCount == 0 && numLines == maxLines - 2)){
//					toReturn.add(sb.toString());
//					sb = new StringBuilder();
//					pageCount++;
//					numLines = 0;
//				}
//				continue;
//			}else if (len + wordWidth > lineWidth){
//				sb.append(curLine);
//				curLine = "";
//				len = 0;
//				numLines++;
//				if ((numLines == maxLines && pageCount > 0) || (pageCount == 0 && numLines == maxLines - 2)){
//					toReturn.add(sb.toString());
//					sb = new StringBuilder();
//					pageCount++;
//					numLines = 0;
//				}
//			}
//			curLine = curLine + " " + s;
//			len += wordWidth + 1;
//		}
//
//		sb.append(curLine);
//
//		if (sb.length() > 0){
//			toReturn.add(sb.toString());
//		}
//
//		return toReturn;
//	}
//
	public void drawTexturedModalRect_Classic(int dst_x, int dst_y, int src_x, int src_y, int dst_width, int dst_height, int src_width, int src_height){
		float var7 = 0.00390625F;
		float var8 = 0.00390625F;

		Tessellator var9 = Tessellator.getInstance();
		var9.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
		var9.getBuffer().pos(dst_x + 0, dst_y + dst_height, this.zLevel).tex((src_x + 0) * var7, (src_y + src_height) * var8).endVertex();
		var9.getBuffer().pos(dst_x + dst_width, dst_y + dst_height, this.zLevel).tex((src_x + src_width) * var7, (src_y + src_height) * var8).endVertex();
		var9.getBuffer().pos(dst_x + dst_width, dst_y + 0, this.zLevel).tex((src_x + src_width) * var7, (src_y + 0) * var8).endVertex();
		var9.getBuffer().pos(dst_x + 0, dst_y + 0, this.zLevel).tex((src_x + 0) * var7, (src_y + 0) * var8).endVertex();
		var9.draw();
	}
//
//	private void DrawIconAtXY(TextureAtlasSprite icon, float x, float y, int w, int h){
//
//		if (icon == null) return;
//
//		Tessellator tessellator = Tessellator.getInstance();
//		tessellator.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
//
//		tessellator.getBuffer().pos(x, y + h, this.zLevel).tex(icon.getMinU(), icon.getMaxV()).endVertex();
//		tessellator.getBuffer().pos(x + w, y + h, this.zLevel).tex(icon.getMaxU(), icon.getMaxV()).endVertex();
//		tessellator.getBuffer().pos(x + w, y, this.zLevel).tex(icon.getMaxU(), icon.getMinV()).endVertex();
//		tessellator.getBuffer().pos(x, y, this.zLevel).tex(icon.getMinU(), icon.getMinV()).endVertex();
//
//		tessellator.draw();
//	}
//
//	@Override
//	public void onGuiClosed(){
//
//		ArcaneCompendium.instance.saveUnlockData();
//
//		super.onGuiClosed();
//	}
	
	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}