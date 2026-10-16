package com.sfb.systemgroups;

import java.util.List;
import java.util.Map;

/**
 * Reading saved-game state back in, defensively.
 *
 * <h2>Why this exists rather than a cast at each site</h2>
 * A saved game is written as JSON and comes back through a parser, so what went out as an
 * {@code int[]} returns as a {@code List<Integer>}, an {@code int} may arrive as a {@code Long} or
 * a {@code Double}, and anything a save predates is simply absent. Every one of those is a cast
 * that throws at load time — the worst moment to fail, because the player has already lost the
 * battle they were trying to resume.
 *
 * <p>So each accessor here answers one question — "is there a usable value under this key?" — and
 * returns null when there is not, leaving the caller's built-from-hull-data value in place. A save
 * missing a field must still load; that is what lets a format grow without orphaning saves.
 */
public final class StateIo {

	private StateIo() {
	}

	/** An int array from either an {@code int[]} or a JSON list of numbers; null if unusable. */
	public static int[] intArray(Object raw) {
		if (raw instanceof int[] direct)
			return direct.clone();
		if (raw instanceof List<?> list) {
			int[] out = new int[list.size()];
			for (int i = 0; i < out.length; i++) {
				if (!(list.get(i) instanceof Number n))
					return null;
				out[i] = n.intValue();
			}
			return out;
		}
		return null;
	}

	/** A double array from either a {@code double[]} or a JSON list of numbers. */
	public static double[] doubleArray(Object raw) {
		if (raw instanceof double[] direct)
			return direct.clone();
		if (raw instanceof List<?> list) {
			double[] out = new double[list.size()];
			for (int i = 0; i < out.length; i++) {
				if (!(list.get(i) instanceof Number n))
					return null;
				out[i] = n.doubleValue();
			}
			return out;
		}
		return null;
	}

	/** A boolean array from either a {@code boolean[]} or a JSON list of booleans. */
	public static boolean[] boolArray(Object raw) {
		if (raw instanceof boolean[] direct)
			return direct.clone();
		if (raw instanceof List<?> list) {
			boolean[] out = new boolean[list.size()];
			for (int i = 0; i < out.length; i++) {
				if (!(list.get(i) instanceof Boolean b))
					return null;
				out[i] = b;
			}
			return out;
		}
		return null;
	}

	/**
	 * Force an array to an exact length, padding with zeroes or truncating.
	 *
	 * The guard against a save and a hull disagreeing about size. A six-shield array restored
	 * onto a single-shield ground base, or the reverse, would otherwise be an
	 * ArrayIndexOutOfBounds somewhere far away from here — and the shape is the HULL'S to state,
	 * never the save's, so the hull wins.
	 */
	public static int[] sized(int[] values, int length) {
		if (values.length == length)
			return values;
		int[] out = new int[length];
		System.arraycopy(values, 0, out, 0, Math.min(values.length, length));
		return out;
	}

	public static int integer(Map<String, Object> state, String key, int fallback) {
		return state != null && state.get(key) instanceof Number n ? n.intValue() : fallback;
	}

	public static double real(Map<String, Object> state, String key, double fallback) {
		return state != null && state.get(key) instanceof Number n ? n.doubleValue() : fallback;
	}

	public static boolean flag(Map<String, Object> state, String key, boolean fallback) {
		return state != null && state.get(key) instanceof Boolean b ? b : fallback;
	}

	public static String text(Map<String, Object> state, String key) {
		return state != null && state.get(key) instanceof String s ? s : null;
	}

	/** A nested state map, or null. Unchecked by necessity: JSON objects arrive as raw maps. */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> child(Map<String, Object> state, String key) {
		return state != null && state.get(key) instanceof Map<?, ?> m
				? (Map<String, Object>) m : null;
	}
}
