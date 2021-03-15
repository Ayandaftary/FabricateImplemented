package com.simibubi.create.content.contraptions.relays.encased;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiPredicate;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

public class CasingConnectivity {

	private Map<Block, Entry> entries;

	public CasingConnectivity() {
		entries = new IdentityHashMap<>();
	}

	public Entry get(BlockState blockState) {
		return entries.get(blockState.getBlock());
	}

	public void makeCasing(Block block, CTSpriteShiftEntry casing) {
		new Entry(block, casing, (s, f) -> true).register();
	}

	public void make(Block block, CTSpriteShiftEntry casing) {
		new Entry(block, casing, (s, f) -> true).register();
	}

	public void make(Block block, CTSpriteShiftEntry casing, BiPredicate<BlockState, Direction> predicate) {
		new Entry(block, casing, predicate).register();
	}

	public class Entry {

		private Block block;
		private CTSpriteShiftEntry casing;
		private BiPredicate<BlockState, Direction> predicate;

		private Entry(Block block, CTSpriteShiftEntry casing, BiPredicate<BlockState, Direction> predicate) {
			this.block = block;
			this.casing = casing;
			this.predicate = predicate;
		}

		public CTSpriteShiftEntry getCasing() {
			return casing;
		}

		public boolean isSideValid(BlockState state, Direction face) {
			return predicate.test(state, face);
		}

		public void register() {
			entries.put(block, this);
		}

	}
}
