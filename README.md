# WOM Clan Stats

WOM Clan Stats brings your Wise Old Man group into RuneLite so you can keep an eye on clan members without leaving the client.

It adds a RuneLite sidebar panel with your clan summary, a searchable member list, manual syncing, automatic refreshes, and an optional larger window for sortable tables and recent activity.

## Overview

- Clan overview with name, clan chat, member count, total XP, total EHP, and total EHB
- Searchable member list in the RuneLite sidebar
- Member roles shown next to each player
- Manual `Sync Now` button
- Automatic refresh every hour, if enabled
- Larger `GUI` window for sortable member stats
- Recent achievements, joins, leaves, and name changes in the expanded window

## Setup

1. Open your clan or group on [Wise Old Man](https://wiseoldman.net).
2. Copy the numeric group ID from the URL.
3. Open RuneLite settings.
4. Find `WOM Clan Stats`.
5. Paste the ID into `WOM Group ID`.
6. Open the WOM Clan Stats sidebar and click `Sync Now`.

For example, if the Wise Old Man URL ends with `/groups/2300`, your group ID is `2300`.

## Using The Sidebar

The sidebar is the main view for day-to-day use.

At the top, you can:

- Click `Sync Now` to fetch the latest Wise Old Man data
- Open the larger table view with `Open GUI`
- See whether the plugin has synced recently

Below that, the clan summary shows:

- Members
- XP
- EHP
- EHB

The search bar filters the member list by player name. The list updates as you type.

## Expanded Window

Click `Open GUI` to open a separate window with more detailed tables.

The `Members` tab includes:

- Rank
- Name
- Role
- Total XP
- EHP
- EHB

You can sort the table columns and use the search field to filter members.

The expanded window also includes tabs for recent achievements, recent group activity, and recent clan name changes.

## Settings

| Setting | What it does |
|---|---|
| `WOM Group ID` | The Wise Old Man group to sync |
| `Auto-refresh (every 1 hour)` | Automatically refreshes data while RuneLite is running |

Auto-refresh is enabled by default. You can still use `Sync Now` whenever you want an immediate update, subject to the 5 minute manual sync cooldown.

## Troubleshooting

If the panel says `Set Group ID in config`, add your Wise Old Man group ID in the plugin settings.

If syncing fails, check that:

- The group ID is correct
- Wise Old Man is reachable
- RuneLite has an internet connection
- The group exists on Wise Old Man

If the member list is empty after syncing, confirm that the Wise Old Man group has members listed on the website.

## Local Development

This section is only needed if you are building or testing the plugin from source.

Run the plugin in RuneLite developer mode:

```bash
./gradlew run
```

Run tests:

```bash
./gradlew test
```

Build the project:

```bash
./gradlew build
```

Build a fat jar:

```bash
./gradlew shadowJar
```
