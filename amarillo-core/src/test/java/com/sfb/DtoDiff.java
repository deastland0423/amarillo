package com.sfb;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflectively compare two DTO graphs and name every field that differs.
 *
 * <h2>Why the saved-game tests are built on this</h2>
 * A save does not fail loudly. It quietly forgets one field, and a battle resumes with a shield
 * back up or a photon re-armed. The field nobody thought to save is also the field nobody thinks
 * to assert, so a hand-written list of assertions misses exactly the case that matters.
 *
 * <p>So the witness is the DTO layer — hundreds of fields extracted for the client, maintained for
 * its own reasons and entirely independently of any save format. Round-trip a game through save
 * and reload, compare the two DTO graphs here, and anything the client can see that the save
 * cannot carry is reported BY NAME.
 *
 * <p>It is a broad net, not a complete one: a value the DTO never exposes cannot be witnessed
 * here, and those need assertions of their own. Treat a clean diff as "nothing the client can see
 * was lost", never as "nothing was lost".
 */
final class DtoDiff {

	private DtoDiff() {
	}

	/** Every differing field, as {@code path: before != after}. Empty means identical. */
	static List<String> between(Object before, Object after) {
		List<String> out = new ArrayList<>();
		compare("", before, after, out, new IdentityHashMap<>());
		return out;
	}

	/**
	 * The top-level field name a difference belongs to, so a caller can group by it.
	 *
	 * {@code "shields[0].current"} and {@code "shields[3].current"} are both {@code shields}.
	 */
	static String topLevel(String difference) {
		int colon = difference.indexOf(':');
		String path = colon < 0 ? difference : difference.substring(0, colon);
		int dot = path.indexOf('.');
		int bracket = path.indexOf('[');
		int cut = dot < 0 ? bracket : (bracket < 0 ? dot : Math.min(dot, bracket));
		return cut < 0 ? path : path.substring(0, cut);
	}

	private static void compare(String path, Object a, Object b, List<String> out,
			IdentityHashMap<Object, Object> seen) {
		if (a == null && b == null)
			return;
		if (a == null || b == null) {
			out.add(path + ": " + describe(a) + " != " + describe(b));
			return;
		}
		if (a.getClass() != b.getClass()) {
			out.add(path + ": " + a.getClass().getSimpleName() + " != "
					+ b.getClass().getSimpleName());
			return;
		}
		Class<?> type = a.getClass();

		if (type.isArray() || isLeaf(type)) {
			if (!java.util.Objects.deepEquals(a, b))
				out.add(path + ": " + describe(a) + " != " + describe(b));
			return;
		}
		// Only guard cycles for the composite types we descend into. Interning makes this
		// unsafe for leaves — every "" and every boxed small Integer is the same instance, so
		// seeing one twice is normal and must not stop the walk.
		if (seen.put(a, b) != null)
			return;

		if (a instanceof List<?> listA && b instanceof List<?> listB) {
			if (listA.size() != listB.size()) {
				out.add(path + ".size: " + listA.size() + " != " + listB.size());
				return;
			}
			for (int i = 0; i < listA.size(); i++)
				compare(path + "[" + i + "]", listA.get(i), listB.get(i), out, seen);
			return;
		}
		if (a instanceof Map<?, ?> mapA && b instanceof Map<?, ?> mapB) {
			if (!mapA.keySet().equals(mapB.keySet())) {
				out.add(path + ".keys: " + mapA.keySet() + " != " + mapB.keySet());
				return;
			}
			for (Object key : mapA.keySet())
				compare(path + "[" + key + "]", mapA.get(key), mapB.get(key), out, seen);
			return;
		}
		for (Field field : type.getFields()) {
			if (Modifier.isStatic(field.getModifiers()))
				continue;
			try {
				compare(path.isEmpty() ? field.getName() : path + "." + field.getName(),
						field.get(a), field.get(b), out, seen);
			} catch (IllegalAccessException e) {
				throw new AssertionError(e);
			}
		}
	}

	private static boolean isLeaf(Class<?> type) {
		return type.isPrimitive() || type.isEnum()
				|| Number.class.isAssignableFrom(type)
				|| type == String.class || type == Boolean.class || type == Character.class;
	}

	private static String describe(Object value) {
		if (value instanceof int[] ints)
			return Arrays.toString(ints);
		if (value instanceof double[] doubles)
			return Arrays.toString(doubles);
		if (value instanceof boolean[] booleans)
			return Arrays.toString(booleans);
		if (value instanceof Object[] objects)
			return Arrays.deepToString(objects);
		return String.valueOf(value);
	}
}
