package dev.latvian.mods.kubejs.recipe.schema;

import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.DevProperties;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.item.InputItem;
import dev.latvian.mods.kubejs.item.OutputItem;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.RecipeTypeFunction;
import dev.latvian.mods.kubejs.util.JsonIO;
import dev.latvian.mods.rhino.util.RemapForJS;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A recipe schema is a set of keys that defines how a recipe is constructed
 * from both KubeJS scripts (through the {@link #constructors}) and JSON files
 * (using the {@link #deserialize(RecipeTypeFunction, ResourceLocation, JsonObject)} method).
 * <p>
 * The schema also defines a {@link #factory} in order to create a {@link KubeRecipe} object that
 * implements serialization logic for certain types of inputs or outputs through for example
 * {@link KubeRecipe#readInputItem(Object)}, post-load validation ({@link KubeRecipe#afterLoaded()}),
 * as well as entirely custom logic such as additional methods a developer may call from scripts.
 *
 * @see RecipeKey
 * @see KubeRecipe
 */
public class RecipeSchema {
	public static final Function<KubeRecipe, String> DEFAULT_UNIQUE_ID_FUNCTION = r -> null;

	private final UUID uuid;
	public final Class<? extends KubeRecipe> recipeType;
	public final Supplier<? extends KubeRecipe> factory;
	public final RecipeKey<?>[] keys;
	private int inputCount;
	private int outputCount;
	private int minRequiredArguments;
	private Int2ObjectMap<RecipeConstructor> constructors;
	public Function<KubeRecipe, String> uniqueIdFunction;

	/**
	 * Default constructor that uses {@link KubeRecipe} as the default recipe factory.
	 *
	 * @param keys The keys that define this schema.
	 * @see #RecipeSchema(Class, Supplier, RecipeKey...)
	 */
	public RecipeSchema(RecipeKey<?>... keys) {
		this(KubeRecipe.class, KubeRecipe::new, keys);
	}

	/**
	 * Defines a new recipe schema that creates recipes of the given {@link KubeRecipe} subclass.
	 * <p>
	 * Keys are defined in order of their appearance in the autogenerated constructor, where optional keys
	 * must be placed after all required keys.
	 *
	 * @param recipeType The type of recipe object this schema creates.
	 * @param factory    A factory to create a new instance of the recipe object. (This is passed to scripts)
	 * @param keys       The keys that define this schema.
	 */
	public RecipeSchema(Class<? extends KubeRecipe> recipeType, Supplier<? extends KubeRecipe> factory, RecipeKey<?>... keys) {
		this.uuid = UUID.randomUUID();
		this.recipeType = recipeType;
		this.factory = factory;
		this.keys = keys;
		this.minRequiredArguments = 0;
		this.inputCount = 0;
		this.outputCount = 0;

		var set = new HashSet<String>();

		for (int i = 0; i < keys.length; i++) {
			if (keys[i].optional()) {
				if (minRequiredArguments == 0) {
					minRequiredArguments = i;
				}
			} else if (minRequiredArguments > 0) {
				throw new IllegalArgumentException("Required key '" + keys[i].name + "' must be ahead of optional keys!");
			}

			if (!set.add(keys[i].name)) {
				throw new IllegalArgumentException("Duplicate key '" + keys[i].name + "' found!");
			}

			if (keys[i].component.role().isInput()) {
				inputCount++;
			} else if (keys[i].component.role().isOutput()) {
				outputCount++;
			}

			if (keys[i].alwaysWrite && keys[i].optional() && keys[i].optional.isDefault()) {
				throw new IllegalArgumentException("Key '" + keys[i] + "' can't have alwaysWrite() enabled with defaultOptional()!");
			}
		}

		if (minRequiredArguments == 0) {
			minRequiredArguments = keys.length;
		}

		this.uniqueIdFunction = DEFAULT_UNIQUE_ID_FUNCTION;
	}

	public UUID uuid() {
		return uuid;
	}

	/**
	 * Defines an additional constructor to be for this schema.
	 *
	 * @param factory The factory that is used to populate the recipe object with data after it is created.
	 * @param keys    The arguments that this constructor takes in.
	 * @return This schema.
	 * @implNote If a constructor is manually defined using this method, constructors will not be automatically generated.
	 */
	@RemapForJS("addConstructor") // constructor is a reserved word in TypeScript, so remap this for scripters who use .d.ts files for typing hints
	public RecipeSchema constructor(RecipeConstructor.Factory factory, RecipeKey<?>... keys) {
		var c = new RecipeConstructor(this, keys, factory);

		if (constructors == null) {
			constructors = new Int2ObjectArrayMap<>(keys.length - minRequiredArguments + 1);
		}

		if (constructors.put(c.keys().length, c) != null) {
			throw new IllegalStateException("Constructor with " + c.keys().length + " arguments already exists!");
		}

		return this;
	}

	@RemapForJS("addConstructor") // constructor is a reserved word in TypeScript, so remap this for scripters who use .d.ts files for typing hints
	public RecipeSchema constructor(RecipeKey<?>... keys) {
		return constructor(RecipeConstructor.Factory.DEFAULT, keys);
	}

	public RecipeSchema uniqueId(Function<KubeRecipe, String> uniqueIdFunction) {
		this.uniqueIdFunction = uniqueIdFunction;
		return this;
	}

	public static String normalizeId(String id) {
		if (id.startsWith("minecraft:")) {
			return id.substring(10);
		} else if (id.startsWith("kubejs:")) {
			return id.substring(7);
		} else {
			return id;
		}
	}

	public RecipeSchema uniqueOutputId(RecipeKey<OutputItem> resultItemKey) {
		return uniqueId(r -> {
			var item = r.getValue(resultItemKey);
			return item == null || item.isEmpty() ? null : normalizeId(item.item.kjs$getId()).replace('/', '_');
		});
	}

	public RecipeSchema uniqueOutputArrayId(RecipeKey<OutputItem[]> resultItemKey) {
		return uniqueId(r -> {
			var array = r.getValue(resultItemKey);

			if (array == null || array.length == 0) {
				return null;
			}

			var sb = new StringBuilder();

			for (var item : array) {
				if (!item.isEmpty()) {
					if (!sb.isEmpty()) {
						sb.append('_');
					}

					sb.append(normalizeId(item.item.kjs$getId()).replace('/', '_'));
				}
			}

			return sb.isEmpty() ? null : sb.toString();
		});
	}

	public RecipeSchema uniqueInputId(RecipeKey<InputItem> resultItemKey) {
		return uniqueId(r -> {
			var ingredient = r.getValue(resultItemKey);
			var item = ingredient == null ? null : ingredient.ingredient.kjs$getFirst();
			return item == null || item.isEmpty() ? null : normalizeId(item.kjs$getId()).replace('/', '_');
		});
	}

	public Int2ObjectMap<RecipeConstructor> constructors() {
		if (constructors == null) {
			var keys1 = Arrays.stream(keys).filter(RecipeKey::includeInAutoConstructors).toArray(RecipeKey[]::new);

			constructors = keys1.length == 0 ? new Int2ObjectArrayMap<>() : new Int2ObjectArrayMap<>(keys1.length - minRequiredArguments + 1);
			boolean dev = DevProperties.get().debugInfo;

			if (dev) {
				KubeJS.LOGGER.info("Generating constructors for " + new RecipeConstructor(this, keys1, RecipeConstructor.Factory.DEFAULT));
			}

			for (int a = minRequiredArguments; a <= keys1.length; a++) {
				var k = new RecipeKey<?>[a];
				System.arraycopy(keys1, 0, k, 0, a);
				var c = new RecipeConstructor(this, k, RecipeConstructor.Factory.DEFAULT);
				constructors.put(a, c);

				if (dev) {
					KubeJS.LOGGER.info("> " + a + ": " + c);
				}
			}
		}

		return constructors;
	}

	public int minRequiredArguments() {
		return minRequiredArguments;
	}

	public int inputCount() {
		return inputCount;
	}

	public int outputCount() {
		return outputCount;
	}

	public KubeRecipe deserialize(RecipeTypeFunction type, @Nullable ResourceLocation id, JsonObject json) {
		var r = factory.get();
		r.type = type;
		r.id = id;
		r.json = json;
		r.newRecipe = id == null;
		r.initValues(id == null);

		if (id != null && DevProperties.get().debugInfo) {
			r.originalJson = (JsonObject) JsonIO.copy(json);
		}

		r.deserialize(false);
		return r;
	}
}
