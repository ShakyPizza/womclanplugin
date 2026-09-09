# Window geometry and sidebar clipping (#17)

## Clipping investigation

Investigated on 2026-09-09 with RuneLite 1.12.38, Temurin 21, XWayland,
and Hyprland. The initial review used 150% desktop scaling; the desktop
was at 125% during the instrumented reproduction. No desktop settings
were changed for this investigation.

Reproduction:

1. Start the development launcher and tile RuneLite beside another window.
2. Open the WOM sidebar with a configured group. Give RuneLite less width
   than its game area plus the open sidebar require.
3. Observe the right-hand controls, values, and toolbar clipped by the
   native window boundary.
4. Float RuneLite so the window manager can honor its minimum size.
   The sidebar becomes reachable without changing the plugin layout.

The following measurements were read from the live Swing component tree.
They are **AWT user-space units**, not screenshot pixels or compositor units:

| Component | Tiled width | Floating width |
| --- | ---: | ---: |
| RuneLite frame | 948 | 1,038 |
| Frame minimum width | 1,038 | 1,038 |
| Content panel | 1,038 | 1,038 |
| Sidebar container | 273 | 273 |
| WOM panel | 242 | 242 |

The game area's minimum width is 765. With the 273-wide sidebar, the
content requires 1,038 units. In the tiled case it exceeds the actual
frame by 90 units. The plugin's own panel is allocated its full width;
the ancestor window clips its right edge.

This matches RuneLite's
[ClientUI.Layout implementation](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-client/src/main/java/net/runelite/client/ui/ClientUI.java#L1413):
it enforces content minimum sizes, places the sidebar after the game
area, and requests a matching frame size. Its
[ContainableFrame](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-client/src/main/java/net/runelite/client/ui/ContainableFrame.java#L126)
also sets a minimum size derived from the layout. In this reproduction,
the tiled native window is smaller than that request.

The responsible boundary is RuneLite's minimum-size layout interacting
with the tiling window manager, rather than an oversized WOM member row.
This does not establish which upstream project should change its behavior.
Do not fix it by changing global desktop rules, forcing RuneLite to float,
or repeatedly resizing the user's game window from this plugin. A tile
large enough for RuneLite's minimum width, or a floating window, keeps
the sidebar reachable. An undersized tile remains a known limitation.

## Expanded-window persistence

`WomWindowGeometry` restores the expanded window once, before it is shown.
It records normal window bounds on move/resize and persists them on close.
Maximized/iconified bounds do not intentionally replace the last normal
bounds. Tiled windows that AWT reports as normal save the actual
window-manager-assigned bounds.

`restoreAndTrack` returns a flush action for the shutdown path. Closing
the window by hand persists through `windowClosed`, but `dispose()` only
*posts* `WINDOW_CLOSED` to the event queue, and during plugin shutdown or
client exit that event may never be dispatched. `WomClanPanel.shutdown`
therefore flushes synchronously before disposing, so a window left open at
exit still records where it was.

Bounds are stored through RuneLite's ConfigManager under
`womclan.window.expandedBounds`, separately from clan data settings so a
geometry save cannot trigger another API refresh. No user-facing settings
are added. Invalid saved values fall back to the default window size,
centered relative to the sidebar.

On restore, the helper chooses the monitor with the largest overlap (or
the nearest center if the old position is off-screen), then clamps the
whole window to that monitor's usable bounds, excluding desktop insets.
It supports negative monitor coordinates and lets a smaller work area
override the normal minimum size. Coordinates stay in AWT user space;
desktop scaling must not be applied a second time.

The window manager remains free to tile or reposition the window after
mapping. The plugin does not fight it with resize loops. Display changes
are handled when the expanded window is reopened, not by moving a window
the user currently has open.

## Validation

- Unit tests cover disconnected monitors, negative coordinates, monitor
  selection, reserved desktop space, reduced work areas, minimum sizes,
  and malformed saved values.
- A live Swing smoke check using an in-memory store showed a frame,
  captured its actual WM-assigned bounds, disposed it, and verified that a
  new tracker restored those bounds before mapping. This also verifies the
  programmatic-dispose path without changing the user's saved settings.
- The wired-up path was exercised end to end against an in-memory store:
  first opening centers at the default size, a saved rectangle is restored
  exactly, an off-screen saved position is clamped back onto the display,
  a malformed value falls back to the default, and the shutdown flush
  writes synchronously without needing the event queue to drain.
- The unit tests stay on the pure geometry functions and pass with
  `java.awt.headless=true`, so they do not require a display in the Plugin
  Hub's build.
- Physical monitor disconnects, Windows/macOS behavior, and native
  maximize/restore transitions have not been manually tested.
