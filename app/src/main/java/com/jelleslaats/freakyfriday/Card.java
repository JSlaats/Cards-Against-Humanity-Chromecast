package com.jelleslaats.freakyfriday;

public class Card {
	public int id;
	public String prompt;

	public Card(int id, String prompt){
		this.id = id;
		this.prompt = prompt;
	}

	public String toString(){
		return this.prompt;
	}
}
