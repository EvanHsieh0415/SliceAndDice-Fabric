package com.possible_triangle.sliceanddice

import com.possible_triangle.sliceanddice.SliceAndDice.MOD_ID
import com.possible_triangle.sliceanddice.block.slicer.*
import com.possible_triangle.sliceanddice.block.sprinkler.SprinkleBehaviour
import com.possible_triangle.sliceanddice.block.sprinkler.SprinklerBlock
import com.possible_triangle.sliceanddice.block.sprinkler.SprinklerTile
import com.possible_triangle.sliceanddice.block.sprinkler.WetAir
import com.possible_triangle.sliceanddice.block.sprinkler.behaviours.BurningBehaviour
import com.possible_triangle.sliceanddice.block.sprinkler.behaviours.FertilizerBehaviour
import com.possible_triangle.sliceanddice.block.sprinkler.behaviours.MoistBehaviour
import com.possible_triangle.sliceanddice.block.sprinkler.behaviours.PotionBehaviour
import com.possible_triangle.sliceanddice.config.Configs
import com.possible_triangle.sliceanddice.recipe.CuttingProcessingRecipe
import com.simibubi.create.AllBlocks
import com.simibubi.create.AllFluids
import com.simibubi.create.AllTags
import com.simibubi.create.content.AllSections
import com.simibubi.create.content.CreateItemGroup
import com.simibubi.create.content.contraptions.components.AssemblyOperatorBlockItem
import com.simibubi.create.content.contraptions.processing.ProcessingRecipeSerializer
import com.simibubi.create.content.logistics.block.mechanicalArm.ArmInteractionPointType
import com.simibubi.create.foundation.block.BlockStressDefaults
import com.simibubi.create.foundation.data.*
import com.tterrag.registrate.fabric.SimpleFlowableFluid
import com.tterrag.registrate.providers.RegistrateRecipeProvider.has
import com.tterrag.registrate.util.nullness.NonNullFunction
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.Registry
import net.minecraft.data.recipes.ShapedRecipeBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.api.ModLoadingContext
import net.minecraftforge.common.data.ExistingFileHelper
import net.minecraftforge.fml.config.ModConfig
import java.util.function.BiFunction
import java.util.function.Supplier


object Content {

    fun modLoc(path: String): ResourceLocation {
        return ResourceLocation(MOD_ID, path)
    }

    private val REGISTRATE = CreateRegistrate.create(MOD_ID).apply {
        creativeModeTab { CreateItemGroup.TAB_TOOLS }
        startSection(AllSections.LOGISTICS)
    }

    val ALLOWED_TOOLS = TagKey.create(Registry.ITEM_REGISTRY, modLoc("allowed_tools"))

    val SLICER_BLOCK = REGISTRATE.block<SlicerBlock>("slicer", ::SlicerBlock).initialProperties(SharedProperties::stone)
        .properties(BlockBehaviour.Properties::noOcclusion).transform(TagGen.axeOrPickaxe()).blockstate { c, p ->
            p.simpleBlock(c.entry, AssetLookup.partialBaseModel(c, p))
        }.addLayer { Supplier { RenderType.cutoutMipped() } }.transform(BlockStressDefaults.setImpact(4.0))
        .item(::AssemblyOperatorBlockItem).transform(ModelGen.customItemModel()).recipe { c, p ->
            ShapedRecipeBuilder.shaped(c.entry).pattern("A").pattern("B").pattern("C")
                .define('A', AllBlocks.COGWHEEL.get()).define('B', AllBlocks.ANDESITE_CASING.get())
                .define('C', AllBlocks.TURNTABLE.get()).unlockedBy("has_tool", has(ALLOWED_TOOLS))
                .unlockedBy("has_mixer", has(AllBlocks.MECHANICAL_MIXER.get())).save(p)
        }.register()

    val SLICER_TILE = REGISTRATE.tileEntity("slicer", ::SlicerTile)
        .instance { BiFunction { manager, tile -> SlicerInstance(manager, tile) } }
        .renderer { NonNullFunction { SlicerRenderer(it) } }.validBlock(SLICER_BLOCK).register()

    private fun <T : Recipe<*>> createRecipeSerializer(
        id: ResourceLocation,
        supplier: Supplier<RecipeSerializer<T>>,
    ): Supplier<RecipeSerializer<T>> {
        val registered = Registry.register(Registry.RECIPE_SERIALIZER, id, supplier.get())
        return Supplier { registered }
    }

    private fun <T : Recipe<*>> createRecipeType(id: ResourceLocation): Supplier<RecipeType<T>> {
        val type = object : RecipeType<T> {
            override fun toString() = id.toString()
        }
        Registry.register(Registry.RECIPE_TYPE, id, type)
        return Supplier { type }
    }

    val CUTTING_RECIPE_TYPE = createRecipeType<CuttingProcessingRecipe>(CuttingProcessingRecipe.id)

    val CUTTING_SERIALIZER = createRecipeSerializer(CuttingProcessingRecipe.id) {
        ProcessingRecipeSerializer(
            ::CuttingProcessingRecipe
        )
    }

    val WET_AIR = REGISTRATE.block<WetAir>("wet_air", ::WetAir).initialProperties { Blocks.CAVE_AIR }
        .properties { it.randomTicks() }.blockstate { c, p ->
            p.simpleBlock(c.entry, p.models().withExistingParent(c.name, "block/barrier"))
        }.register()

    val SPRINKLER_BLOCK = REGISTRATE.block<SprinklerBlock>("sprinkler", ::SprinklerBlock)
        .initialProperties { SharedProperties.copperMetal() }.transform(TagGen.pickaxeOnly())
        .addLayer { Supplier { RenderType.cutoutMipped() } }
        .blockstate { c, p -> p.simpleBlock(c.entry, AssetLookup.standardModel(c, p)) }.item()
        .transform(ModelGen.customItemModel("_")).recipe { c, p ->
            ShapedRecipeBuilder.shaped(c.entry, 3).pattern("SPS").pattern("SBS")
                .define('S', AllTags.forgeItemTag("plates/copper")).define('B', Blocks.IRON_BARS)
                .define('P', AllBlocks.FLUID_PIPE.get()).unlockedBy("has_pipe", has(AllBlocks.FLUID_PIPE.get())).save(p)
        }.register()

    val SPRINKLER_TILE = REGISTRATE.tileEntity("sprinkler", ::SprinklerTile).validBlock(SPRINKLER_BLOCK).register()

    private val WET_FLUIDS = TagKey.create(Registry.FLUID_REGISTRY, modLoc("moisturizing"))
    private val HOT_FLUIDS = TagKey.create(Registry.FLUID_REGISTRY, modLoc("burning"))
    private val FERTILIZERS = TagKey.create(Registry.FLUID_REGISTRY, modLoc("fertilizer"))

    val FERTILIZER_BLACKLIST = TagKey.create(Registry.BLOCK_REGISTRY, modLoc("fertilizer_blacklist"))

    val FERTILIZER =
        REGISTRATE.fluid("fertilizer", modLoc("fluid/fertilizer_still"), modLoc("fluid/fertilizer_flowing"))
            .tag(FERTILIZERS).source { SimpleFlowableFluid.Source(it) }
            .bucket().defaultModel().build()
            .register()

    fun register() {
        REGISTRATE.register()

        ModLoadingContext.registerConfig(MOD_ID, ModConfig.Type.COMMON, Configs.SERVER_SPEC)
        ModLoadingContext.registerConfig(MOD_ID, ModConfig.Type.CLIENT, Configs.CLIENT_SPEC)

        SprinkleBehaviour.register(WET_FLUIDS, MoistBehaviour)
        SprinkleBehaviour.register(HOT_FLUIDS, BurningBehaviour)
        SprinkleBehaviour.register(FERTILIZERS, FertilizerBehaviour)
        SprinkleBehaviour.register({ AllFluids.POTION.`is`(it.fluid) }, PotionBehaviour)

        ArmInteractionPointType.register(SlicerArmInteractionType)
    }

    fun registerData(generator: FabricDataGenerator) {
        PonderScenes.register()
        val helper = ExistingFileHelper.withResourcesFromArg()
        REGISTRATE.setupDatagen(generator, helper)
    }

    fun isWet(state: BlockState): Boolean {
        return WET_AIR.`is`(state)
    }

}