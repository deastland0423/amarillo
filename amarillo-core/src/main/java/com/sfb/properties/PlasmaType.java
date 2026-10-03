package com.sfb.properties;

public enum PlasmaType {

	D("D"),
	F("F"),
	G("G"),
	K("K"),
	S("S"),
	R("R");

	private final String label;

	PlasmaType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
