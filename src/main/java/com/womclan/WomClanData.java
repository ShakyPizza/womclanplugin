package com.womclan;

import lombok.Value;

import java.util.List;

@Value
public class WomClanData
{
	/** The WOM group this data belongs to, so a surface never labels it as another group's. */
	int groupId;

	WomClanInfo info;
	List<WomMember> members;
	WomHistory<WomAchievement> achievements;
	WomHistory<WomGroupActivity> activity;
	WomHistory<WomNameChange> nameChanges;
}
