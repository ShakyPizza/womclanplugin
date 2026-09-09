package com.womclan;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WomApiClientTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void sidebarFetchUsesOneRequestAndDefersHistory() throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		OkHttpClient httpClient = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			requests.incrementAndGet();
			return new Response.Builder()
				.request(chain.request())
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.message("OK")
				.header("RateLimit-Limit", "20")
				.header("RateLimit-Remaining", "19")
				.header("RateLimit-Reset", "60")
				.body(ResponseBody.create(MediaType.parse("application/json"),
					"{\"name\":\"One Request Clan\",\"memberships\":[]}"))
				.build();
		}).build();
		WomApiClient client = new WomApiClient(httpClient,
			new WomClanCache(temporaryFolder.newFolder().toPath()));

		WomClanData data = client.fetchClanData(2300);

		assertEquals(1, requests.get());
		assertEquals(WomHistory.Status.PENDING, data.getAchievements().getStatus());
		assertEquals(20, client.rateLimitStatus().getLimit());
		assertEquals(19, client.rateLimitStatus().getRemaining());
		assertTrue(client.rateLimitStatus().getResetAtMs() > System.currentTimeMillis());
	}

	@Test
	public void parseMembersReadsGroupMemberships() throws IOException
	{
		String json = "{"
			+ "\"id\":2300,"
			+ "\"memberships\":["
			+ "{\"player\":{\"username\":\"alpha\",\"displayName\":\"Alpha\",\"exp\":123,\"ehp\":1.5,\"ehb\":2.5}},"
			+ "{\"player\":{\"username\":\"beta\",\"exp\":456}}"
			+ "]"
			+ "}";

		List<WomMember> members = WomApiClient.parseMembers(json);

		assertEquals(2, members.size());
		assertEquals("Alpha", members.get(0).getDisplayName());
		assertEquals(123L, members.get(0).getTotalXp());
		assertEquals(1.5, members.get(0).getEhp(), 0.0);
		assertEquals(2.5, members.get(0).getEhb(), 0.0);
		assertEquals("beta", members.get(1).getDisplayName());
		assertEquals(456L, members.get(1).getTotalXp());
		assertEquals(0.0, members.get(1).getEhp(), 0.0);
		assertEquals(0.0, members.get(1).getEhb(), 0.0);
	}

	@Test
	public void parseClanInfoReadsMetadataAndTotals() throws IOException
	{
		String json = "{"
			+ "\"id\":2300,"
			+ "\"name\":\"Wise Old Clan\","
			+ "\"clanChat\":\"WOM CC\","
			+ "\"memberCount\":2,"
			+ "\"memberships\":["
			+ "{\"player\":{\"username\":\"alpha\",\"displayName\":\"Alpha\",\"exp\":123,\"ehp\":1.5,\"ehb\":2.5}},"
			+ "{\"player\":{\"username\":\"beta\",\"exp\":456,\"ehp\":3.5,\"ehb\":4.5}}"
			+ "]"
			+ "}";

		List<WomMember> members = WomApiClient.parseMembers(json);
		WomClanInfo info = WomApiClient.parseClanInfo(json, members);

		assertEquals("Wise Old Clan", info.getName());
		assertEquals("WOM CC", info.getClanChat());
		assertEquals(2, info.getMemberCount());
		assertEquals(579L, info.getTotalXp());
		assertEquals(5.0, info.getTotalEhp(), 0.0);
		assertEquals(7.0, info.getTotalEhb(), 0.0);
	}

	@Test
	public void parseAchievementsReadsRecentMilestones() throws IOException
	{
		String json = "["
			+ "{"
			+ "\"name\":\"Base 70 Stats\","
			+ "\"metric\":\"overall\","
			+ "\"threshold\":737627,"
			+ "\"measure\":\"levels\","
			+ "\"createdAt\":\"2022-10-28T12:42:24.215Z\","
			+ "\"player\":{\"username\":\"alpha\",\"displayName\":\"Alpha\"}"
			+ "}"
			+ "]";

		List<WomAchievement> achievements = WomApiClient.parseAchievements(json);

		assertEquals(1, achievements.size());
		assertEquals("Alpha", achievements.get(0).getDisplayName());
		assertEquals("Base 70 Stats", achievements.get(0).getName());
		assertEquals("overall", achievements.get(0).getMetric());
		assertEquals("levels", achievements.get(0).getMeasure());
		assertEquals(737627L, achievements.get(0).getThreshold());
		assertEquals(Instant.parse("2022-10-28T12:42:24.215Z"), achievements.get(0).getCreatedAt());
	}

	@Test
	public void parseActivityReadsMembershipChanges() throws IOException
	{
		String json = "["
			+ "{"
			+ "\"type\":\"joined\","
			+ "\"role\":null,"
			+ "\"createdAt\":\"2023-10-16T13:20:50.273Z\","
			+ "\"player\":{\"username\":\"beta\",\"displayName\":\"Beta\"}"
			+ "},"
			+ "{"
			+ "\"type\":\"changed_role\","
			+ "\"role\":\"iron\","
			+ "\"createdAt\":\"2023-10-23T20:39:45.104Z\","
			+ "\"player\":{\"username\":\"gamma\"}"
			+ "}"
			+ "]";

		List<WomGroupActivity> activity = WomApiClient.parseActivity(json);

		assertEquals(2, activity.size());
		assertEquals("Beta", activity.get(0).getDisplayName());
		assertEquals("joined", activity.get(0).getType());
		assertEquals("", activity.get(0).getRole());
		assertEquals(Instant.parse("2023-10-16T13:20:50.273Z"), activity.get(0).getCreatedAt());
		assertEquals("gamma", activity.get(1).getDisplayName());
		assertEquals("changed_role", activity.get(1).getType());
		assertEquals("iron", activity.get(1).getRole());
	}

	@Test
	public void parseNameChangesReadsRecentGroupNameChanges() throws IOException
	{
		String json = "["
			+ "{"
			+ "\"oldName\":\"Old Alpha\","
			+ "\"newName\":\"New Alpha\","
			+ "\"status\":\"approved\","
			+ "\"resolvedAt\":\"2024-01-02T03:04:05.000Z\","
			+ "\"createdAt\":\"2024-01-01T03:04:05.000Z\","
			+ "\"player\":{\"username\":\"new alpha\",\"displayName\":\"New Alpha\"}"
			+ "},"
			+ "{"
			+ "\"oldName\":\"Old Beta\","
			+ "\"newName\":\"New Beta\","
			+ "\"status\":\"pending\","
			+ "\"resolvedAt\":null,"
			+ "\"createdAt\":\"2024-02-01T03:04:05.000Z\""
			+ "}"
			+ "]";

		List<WomNameChange> nameChanges = WomApiClient.parseNameChanges(json);

		assertEquals(2, nameChanges.size());
		assertEquals("New Alpha", nameChanges.get(0).getDisplayName());
		assertEquals("Old Alpha", nameChanges.get(0).getOldName());
		assertEquals("New Alpha", nameChanges.get(0).getNewName());
		assertEquals("approved", nameChanges.get(0).getStatus());
		assertEquals(Instant.parse("2024-01-02T03:04:05.000Z"), nameChanges.get(0).getResolvedAt());
		assertEquals(Instant.parse("2024-01-01T03:04:05.000Z"), nameChanges.get(0).getCreatedAt());
		assertEquals("New Beta", nameChanges.get(1).getDisplayName());
		assertEquals("pending", nameChanges.get(1).getStatus());
	}
}
