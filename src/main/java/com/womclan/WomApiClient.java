package com.womclan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class WomApiClient
{
	private static final String API_BASE = "https://api.wiseoldman.net/v2";
	/** How many history entries each optional section fetches. The UI names this, so it is shared. */
	static final int HISTORY_LIMIT = 50;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private WomClanCache cache;

	private volatile WomRateLimitStatus rateLimitStatus = WomRateLimitStatus.UNKNOWN;

	public WomApiClient()
	{
	}

	/** Test seam for exercising the complete request path without dependency injection. */
	WomApiClient(OkHttpClient okHttpClient, WomClanCache cache)
	{
		this.okHttpClient = okHttpClient;
		this.cache = cache;
	}

	/** Fetches the one response needed by the sidebar. History is restored from cache, if present. */
	public WomClanData fetchClanData(int groupId) throws IOException
	{
		String body = fetchBody(API_BASE + "/groups/" + groupId, "group " + groupId);
		long now = System.currentTimeMillis();
		cache.storeGroup(groupId, body, now);
		List<WomMember> members = parseMembers(body);
		WomClanCache.CachedClan cached = cache.load(groupId);

		log.debug("Fetched {} members for group {}", members.size(), groupId);
		return new WomClanData(
			groupId,
			parseClanInfo(body, members),
			members,
			cached == null ? WomHistory.pending() : cached.getData().getAchievements(),
			cached == null ? WomHistory.pending() : cached.getData().getActivity(),
			cached == null ? WomHistory.pending() : cached.getData().getNameChanges()
		);
	}

	/** Restores the most recent successful response immediately, without touching the network. */
	WomClanCache.CachedClan loadCachedClanData(int groupId)
	{
		return cache.load(groupId);
	}

	/**
	 * Fetches the optional detail feeds. This is called only when Clan Details needs them, rather
	 * than spending three requests on every sidebar refresh.
	 */
	WomClanData fetchClanHistory(int groupId, WomClanData base) throws IOException
	{
		WomHistory<WomAchievement> achievements = fetchHistory(groupId, "achievements", () ->
		{
			String body = fetchBody(API_BASE + "/groups/" + groupId + "/achievements?limit=" + HISTORY_LIMIT,
				"group achievements " + groupId);
			cache.storeAchievements(groupId, body, System.currentTimeMillis());
			return parseAchievements(body);
		});

		WomHistory<WomGroupActivity> activity = fetchHistory(groupId, "activity", () ->
		{
			String body = fetchBody(API_BASE + "/groups/" + groupId + "/activity?limit=" + HISTORY_LIMIT,
				"group activity " + groupId);
			cache.storeActivity(groupId, body, System.currentTimeMillis());
			return parseActivity(body);
		});

		WomHistory<WomNameChange> nameChanges = fetchHistory(groupId, "name changes", () ->
		{
			String body = fetchBody(API_BASE + "/groups/" + groupId + "/name-changes?limit=" + HISTORY_LIMIT,
				"group name changes " + groupId);
			cache.storeNameChanges(groupId, body, System.currentTimeMillis());
			return parseNameChanges(body);
		});

		return new WomClanData(groupId, base.getInfo(), base.getMembers(), achievements, activity, nameChanges);
	}

	WomRateLimitStatus rateLimitStatus()
	{
		return rateLimitStatus;
	}

	/**
	 * Fetches all members of a WOM group and their stats.
	 *
	 * @param groupId the Wise Old Man group ID
	 * @return list of WomMember, sorted by the caller
	 * @throws IOException on network or non-2xx response
	 */
	public List<WomMember> fetchMembers(int groupId) throws IOException
	{
		String body = fetchBody(API_BASE + "/groups/" + groupId, "group " + groupId);
		List<WomMember> members = parseMembers(body);

		log.debug("Fetched {} members for group {}", members.size(), groupId);
		return members;
	}

	public List<WomAchievement> fetchAchievements(int groupId) throws IOException
	{
		String body = fetchBody(
			API_BASE + "/groups/" + groupId + "/achievements?limit=" + HISTORY_LIMIT,
			"group achievements " + groupId
		);
		List<WomAchievement> achievements = parseAchievements(body);

		log.debug("Fetched {} achievements for group {}", achievements.size(), groupId);
		return achievements;
	}

	public List<WomGroupActivity> fetchActivity(int groupId) throws IOException
	{
		String body = fetchBody(
			API_BASE + "/groups/" + groupId + "/activity?limit=" + HISTORY_LIMIT,
			"group activity " + groupId
		);
		List<WomGroupActivity> activity = parseActivity(body);

		log.debug("Fetched {} activity entries for group {}", activity.size(), groupId);
		return activity;
	}

	public List<WomNameChange> fetchNameChanges(int groupId) throws IOException
	{
		String body = fetchBody(
			API_BASE + "/groups/" + groupId + "/name-changes?limit=" + HISTORY_LIMIT,
			"group name changes " + groupId
		);
		List<WomNameChange> nameChanges = parseNameChanges(body);

		log.debug("Fetched {} name changes for group {}", nameChanges.size(), groupId);
		return nameChanges;
	}

	/**
	 * Runs one best-effort history fetch. These sections are optional — a member list is still worth
	 * showing without them — but a failure is recorded rather than flattened into an empty list, so
	 * the UI can tell "could not load" apart from "nothing happened recently".
	 */
	private <T> WomHistory<T> fetchHistory(int groupId, String noun, HistoryFetch<T> fetch) throws IOException
	{
		try
		{
			return WomHistory.loaded(fetch.run());
		}
		catch (IOException e)
		{
			// A 429 establishes a server deadline. Let the caller stop the detail sequence instead
			// of turning it into three immediate rejected requests.
			if (e instanceof WomApiException && ((WomApiException) e).getStatusCode() == 429)
			{
				throw e;
			}
			log.warn("WOM Clan Stats: failed to fetch {} for group {}: {}", noun, groupId, e.getMessage());
			return WomHistory.unavailable(e.getMessage());
		}
	}

	@FunctionalInterface
	private interface HistoryFetch<T>
	{
		List<T> run() throws IOException;
	}

	static List<WomMember> parseMembers(String body) throws IOException
	{
		JsonObject root = new JsonParser().parse(body).getAsJsonObject();
		JsonArray memberships = root.has("memberships") && root.get("memberships").isJsonArray()
			? root.getAsJsonArray("memberships")
			: new JsonArray();

		List<WomMember> members = new ArrayList<>();
		for (JsonElement elem : memberships)
		{
			JsonObject obj = elem.getAsJsonObject();
			if (!obj.has("player") || obj.get("player").isJsonNull())
			{
				continue;
			}

			JsonObject player = obj.getAsJsonObject("player");
			if (!player.has("username") || player.get("username").isJsonNull())
			{
				continue;
			}

			String displayName = player.has("displayName") && !player.get("displayName").isJsonNull()
				? player.get("displayName").getAsString()
				: player.get("username").getAsString();

			String role = obj.has("role") && !obj.get("role").isJsonNull()
				? obj.get("role").getAsString()
				: "member";

			long totalXp = player.has("exp") && !player.get("exp").isJsonNull()
				? player.get("exp").getAsLong()
				: 0L;

			double ehp = player.has("ehp") && !player.get("ehp").isJsonNull()
				? player.get("ehp").getAsDouble()
				: 0.0;

			double ehb = player.has("ehb") && !player.get("ehb").isJsonNull()
				? player.get("ehb").getAsDouble()
				: 0.0;

			members.add(new WomMember(displayName, role, totalXp, ehp, ehb));
		}

		return members;
	}

	static WomClanInfo parseClanInfo(String body, List<WomMember> members) throws IOException
	{
		JsonObject root = new JsonParser().parse(body).getAsJsonObject();
		String name = readString(root, "name", "Clan");
		String clanChat = readString(root, "clanChat", "");
		int memberCount = root.has("memberCount") && !root.get("memberCount").isJsonNull()
			? root.get("memberCount").getAsInt()
			: members.size();

		long totalXp = 0L;
		double totalEhp = 0.0;
		double totalEhb = 0.0;
		for (WomMember member : members)
		{
			totalXp += member.getTotalXp();
			totalEhp += member.getEhp();
			totalEhb += member.getEhb();
		}

		return new WomClanInfo(name, clanChat, memberCount, totalXp, totalEhp, totalEhb);
	}

	static List<WomAchievement> parseAchievements(String body) throws IOException
	{
		JsonArray root = new JsonParser().parse(body).getAsJsonArray();

		List<WomAchievement> achievements = new ArrayList<>();
		for (JsonElement elem : root)
		{
			JsonObject obj = elem.getAsJsonObject();
			String displayName = readPlayerDisplayName(obj);
			if (displayName == null)
			{
				continue;
			}

			achievements.add(new WomAchievement(
				displayName,
				readString(obj, "name", "Achievement"),
				readString(obj, "metric", ""),
				readString(obj, "measure", ""),
				readLong(obj, "threshold", 0L),
				readInstant(obj, "createdAt")
			));
		}

		return achievements;
	}

	static List<WomGroupActivity> parseActivity(String body) throws IOException
	{
		JsonArray root = new JsonParser().parse(body).getAsJsonArray();

		List<WomGroupActivity> activity = new ArrayList<>();
		for (JsonElement elem : root)
		{
			JsonObject obj = elem.getAsJsonObject();
			String displayName = readPlayerDisplayName(obj);
			if (displayName == null)
			{
				continue;
			}

			activity.add(new WomGroupActivity(
				displayName,
				readString(obj, "type", ""),
				readString(obj, "role", ""),
				readInstant(obj, "createdAt")
			));
		}

		return activity;
	}

	static List<WomNameChange> parseNameChanges(String body) throws IOException
	{
		JsonArray root = new JsonParser().parse(body).getAsJsonArray();

		List<WomNameChange> nameChanges = new ArrayList<>();
		for (JsonElement elem : root)
		{
			JsonObject obj = elem.getAsJsonObject();
			String oldName = readString(obj, "oldName", "");
			String newName = readString(obj, "newName", "");
			String displayName = readPlayerDisplayName(obj);
			if (displayName == null)
			{
				displayName = newName.isEmpty() ? oldName : newName;
			}

			nameChanges.add(new WomNameChange(
				displayName,
				oldName,
				newName,
				readString(obj, "status", ""),
				readInstant(obj, "resolvedAt"),
				readInstant(obj, "createdAt")
			));
		}

		return nameChanges;
	}

	private String fetchBody(String url, String context) throws IOException
	{
		long now = System.currentTimeMillis();
		if (rateLimitStatus.getRetryAtMs() > now)
		{
			throw new WomApiException("WOM API retry available later", 429, rateLimitStatus.getRetryAtMs());
		}

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", "WomClanStats-RuneLitePlugin/1.0")
			.build();

		try (Response response = okHttpClient.newCall(request).execute())
		{
			captureRateLimit(response, System.currentTimeMillis());
			if (!response.isSuccessful())
			{
				throw new WomApiException(
					"WOM API error " + response.code() + " for " + context,
					response.code(),
					rateLimitStatus.getRetryAtMs());
			}

			ResponseBody responseBody = response.body();
			if (responseBody == null)
			{
				throw new IOException("Empty response body from WOM API for " + context);
			}

			return responseBody.string();
		}
	}

	private void captureRateLimit(Response response, long nowMs)
	{
		int limit = headerInt(response, "RateLimit-Limit", -1);
		int remaining = headerInt(response, "RateLimit-Remaining", -1);
		long resetAt = deadline(nowMs, headerInt(response, "RateLimit-Reset", 0));
		long retryAt = deadline(nowMs, headerInt(response, "Retry-After", 0));
		// The last permitted response has no Retry-After yet, but Remaining: 0 already tells us
		// that another request in this window would be rejected.
		if (remaining == 0 && retryAt == 0)
		{
			retryAt = resetAt;
		}
		rateLimitStatus = new WomRateLimitStatus(limit, remaining, resetAt, retryAt);
	}

	private static int headerInt(Response response, String name, int defaultValue)
	{
		String value = response.header(name);
		if (value == null)
		{
			return defaultValue;
		}
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			return defaultValue;
		}
	}

	private static long deadline(long nowMs, int seconds)
	{
		return seconds <= 0 ? 0 : nowMs + seconds * 1_000L;
	}

	private static String readPlayerDisplayName(JsonObject obj)
	{
		if (!obj.has("player") || obj.get("player").isJsonNull())
		{
			return null;
		}

		JsonObject player = obj.getAsJsonObject("player");
		if (!player.has("username") || player.get("username").isJsonNull())
		{
			return null;
		}

		return player.has("displayName") && !player.get("displayName").isJsonNull()
			? player.get("displayName").getAsString()
			: player.get("username").getAsString();
	}

	private static String readString(JsonObject obj, String field, String defaultValue)
	{
		return obj.has(field) && !obj.get(field).isJsonNull()
			? obj.get(field).getAsString()
			: defaultValue;
	}

	private static long readLong(JsonObject obj, String field, long defaultValue)
	{
		return obj.has(field) && !obj.get(field).isJsonNull()
			? obj.get(field).getAsLong()
			: defaultValue;
	}

	private static Instant readInstant(JsonObject obj, String field)
	{
		if (!obj.has(field) || obj.get(field).isJsonNull())
		{
			return null;
		}

		try
		{
			return Instant.parse(obj.get(field).getAsString());
		}
		catch (DateTimeParseException e)
		{
			return null;
		}
	}
}
