package com.womclan;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Small disk cache of raw WOM responses. Raw JSON avoids coupling the on-disk format to the UI
 * model and lets the normal, tested API parsers validate every restored response.
 */
@Slf4j
@Singleton
class WomClanCache
{
	static final long HISTORY_MAX_AGE_MS = 60 * 60 * 1_000L;

	private final Path directory;
	private final Gson gson;

	@Inject
	WomClanCache(Gson gson)
	{
		this(new File(RuneLite.CACHE_DIR, "wom-clan-stats").toPath(), gson);
	}

	WomClanCache(Path directory, Gson gson)
	{
		this.directory = directory;
		this.gson = gson;
	}

	synchronized CachedClan load(int groupId)
	{
		Record record = read(groupId);
		if (record == null || record.groupBody == null || record.groupBody.isEmpty())
		{
			return null;
		}

		try
		{
			java.util.List<WomMember> members = WomApiClient.parseMembers(record.groupBody);
			WomClanData data = new WomClanData(
				groupId,
				WomApiClient.parseClanInfo(record.groupBody, members),
				members,
				parseHistory(record.achievementsBody, WomApiClient::parseAchievements),
				parseHistory(record.activityBody, WomApiClient::parseActivity),
				parseHistory(record.nameChangesBody, WomApiClient::parseNameChanges)
			);
			long oldestHistoryMs = oldestPresent(
				record.achievementsAtMs, record.activityAtMs, record.nameChangesAtMs);
			return new CachedClan(data, record.groupAtMs, oldestHistoryMs);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("WOM Clan Stats: ignoring unreadable cache for group {}: {}", groupId, e.getMessage());
			return null;
		}
	}

	synchronized void storeGroup(int groupId, String body, long nowMs)
	{
		Record record = readOrNew(groupId);
		record.groupBody = body;
		record.groupAtMs = nowMs;
		write(groupId, record);
	}

	synchronized void storeAchievements(int groupId, String body, long nowMs)
	{
		Record record = readOrNew(groupId);
		record.achievementsBody = body;
		record.achievementsAtMs = nowMs;
		write(groupId, record);
	}

	synchronized void storeActivity(int groupId, String body, long nowMs)
	{
		Record record = readOrNew(groupId);
		record.activityBody = body;
		record.activityAtMs = nowMs;
		write(groupId, record);
	}

	synchronized void storeNameChanges(int groupId, String body, long nowMs)
	{
		Record record = readOrNew(groupId);
		record.nameChangesBody = body;
		record.nameChangesAtMs = nowMs;
		write(groupId, record);
	}

	private Record readOrNew(int groupId)
	{
		Record record = read(groupId);
		return record == null ? new Record() : record;
	}

	private Record read(int groupId)
	{
		Path file = file(groupId);
		if (!Files.isRegularFile(file))
		{
			return null;
		}

		try
		{
			return gson.fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), Record.class);
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("WOM Clan Stats: could not read cache for group {}: {}", groupId, e.getMessage());
			return null;
		}
	}

	private void write(int groupId, Record record)
	{
		try
		{
			Files.createDirectories(directory);
			Path temporary = Files.createTempFile(directory, "group-" + groupId + "-", ".tmp");
			Files.write(temporary, gson.toJson(record).getBytes(StandardCharsets.UTF_8));
			try
			{
				Files.move(temporary, file(groupId), StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				Files.move(temporary, file(groupId), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			// A cache failure must never turn a successful API response into a failed sync.
			log.warn("WOM Clan Stats: could not write cache for group {}: {}", groupId, e.getMessage());
		}
	}

	private Path file(int groupId)
	{
		return directory.resolve("group-" + groupId + ".json");
	}

	private static <T> WomHistory<T> parseHistory(String body, Parser<T> parser) throws IOException
	{
		return body == null || body.isEmpty() ? WomHistory.pending() : WomHistory.loaded(parser.parse(body));
	}

	private static long oldestPresent(long first, long second, long third)
	{
		if (first <= 0 || second <= 0 || third <= 0)
		{
			return 0;
		}
		return Math.min(first, Math.min(second, third));
	}

	@FunctionalInterface
	private interface Parser<T>
	{
		java.util.List<T> parse(String body) throws IOException;
	}

	@Value
	static class CachedClan
	{
		WomClanData data;
		long groupCachedAtMs;
		long historyCachedAtMs;

		boolean historyNeedsRefresh(long nowMs)
		{
			return historyCachedAtMs <= 0 || nowMs - historyCachedAtMs >= HISTORY_MAX_AGE_MS;
		}
	}

	private static class Record
	{
		private String groupBody;
		private long groupAtMs;
		private String achievementsBody;
		private long achievementsAtMs;
		private String activityBody;
		private long activityAtMs;
		private String nameChangesBody;
		private long nameChangesAtMs;
	}
}
