<div align="center">
  
<a href="https://modrinth.com/mod/gracebound/settings/versions?l=neoforge"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/cozy/supported/neoforge_64h.png" alt="Available for NeoForge"></a><br>
<a href="https://modrinth.com/mod/gracebound" target="_blank" rel="noopener noreferrer"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/compact-minimal/available/modrinth_46h.png" alt="Available on Modrinth"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/Gracebound" target="_blank" rel="noopener noreferrer"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/compact-minimal/available/curseforge_46h.png" alt="Available on CurseForge"></a>
<a href="https://github.com/JevenDev/Gracebound" target="_blank" rel="noopener noreferrer"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/compact-minimal/available/github_46h.png" alt="Available on GitHub"></a>
  
</div>

![gracebound banner](https://cdn.modrinth.com/data/cached_images/c94476a12bd54ec3618c1154af93c256ef3c4093.png)

<div align="center">
  <img
    src="https://i.imgur.com/DsFmIdF.gif"
    alt='Grace guidance destination'
    width="49%"
  />
</div>
<br>

Gracebound adds a **guidance trail** inspired by Elden Ring's "Guidance of Grace". It helps point you back toward important locations without opening a map or cluttering your screen.

By default, Gracebound appears after death and guides you back toward your last known death location. If you hold a compass, the guidance changes based on the compass type instead.

- **Death guidance** points toward your last death location `ON / configurable`
- **Recovery compass guidance** points toward your recovery compass target (aka death location) `Configurable / not required if "Death Guidance" is ON`
- **Regular compass guidance** points toward world spawn
- **Lodestone compass guidance** points toward the compass’ linked lodestone 
- **Cross-dimension protection** prevents misleading guidance when the target is in another dimension

If grace cannot guide you across dimensions, the mod will let you know instead of pointing you in the wrong direction.

![features](https://cdn.modrinth.com/data/cached_images/ec0e4dc78ec1a652eb11b233dd2926f7461fe770.png)

<div align="center">
  <img
    src="https://i.imgur.com/b7zoVjR.gif"
    alt='emeralds being automatically stored by the pouch "auto pick-up" feature'
    width="49%"
  />
  <img
    src="https://i.imgur.com/8434y3I.gif"
    alt='emeralds being automatically compacted by the pouch "auto compact" feature'
    width="49%"
  />
</div>
<br>

Gracebound is designed to feel lightweight and atmospheric rather than intrusive. The trail renders as a twirling stream near the player, fading in and out smoothly as guidance becomes available or disappears.


Current functionality includes:

- A configurable **grace guidance trail**
- Automatic death-location guidance
- Compass-based guidance overrides
- Support for regular, recovery, and lodestone compasses
- Fade in/out behavior
- Adjustable beam density, origin, distance, and vertical offset
- Optional client-side guidance messages
- Per-compass guidance toggles

<div align="center">
  <img
    src="https://i.imgur.com/vnPzn3j.gif"
    alt='emeralds being automatically stored by the pouch "auto pick-up" feature'
    width="49%"
  />
  <img
    src="https://i.imgur.com/QqrzDbl.gif"
    alt='emeralds being automatically compacted by the pouch "auto compact" feature'
    width="49%"
  />
</div>
<br>

![keybinds](https://cdn.modrinth.com/data/cached_images/201d5ce49ba16974e3c3b0b562c392e03f38e35f.png)

## Default keybinds

- **Toggle Guidance Rendering** - `]`
  - Cycles between:
    - **On Death**
    - **Always Active**
    - **Off**

## Guidance Modes

- **On Death**
  - Gracebound appears when death guidance is available
  - This is the default mode
- **Always Active**
  - Gracebound stays active whenever a valid guidance target exists
- **Off**
  - Disables Gracebound rendering

![configuration](https://cdn.modrinth.com/data/cached_images/a9bf17e3498b0933f2332ceeb812d0166a889f2e.png)

## Configuration

Gracebound includes in-game/client configuration options for tuning how guidance behaves and looks.

You can configure:

- **Enable Fading**
  - Toggles fade in/out behavior
- **Fade In Ticks**
  - Controls how quickly the guidance appears
- **Fade Out Ticks**
  - Controls how quickly the guidance disappears
- **Beam Density**
  - Controls how many wispy strands are drawn in the stream
- **Beam Origin Offset**
  - Adjusts how far forward the stream starts from the player
- **Beam Start Distance**
  - Adds extra distance before rendering begins
- **Beam Vertical Offset**
  - Moves the trail origin up or down from eye level
- **Max Beam Distance**
  - Controls the maximum visual guidance distance
- **Show Guidance Messages**
  - Toggles small client-side status messages
- **Compass Guidance Toggles**
  - Enable or disable guidance for:
    - lodestone compasses
    - recovery compasses
    - regular compasses

![compatibility](https://cdn.modrinth.com/data/cached_images/1252c11050b7daf8b8621712b58dd1005e7ba982.png)

## Compatibility

Gracebound currently includes optional compatibility support for:

- **[Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)**
  - Adds Gracebound guidance visuals to the minimap when available
- **[Xaero's World Map](https://modrinth.com/mod/xaeros-world-map)**
  - Adds Gracebound guidance visuals to the world map when available
- **[Antique Atlas](https://modrinth.com/mod/antique-atlas-4)**
  - Adds Gracebound guidance overlays to the atlas when available

These mods are optional. Gracebound can still be used without them.

If there is a specific mod you would like compatibility support for, feel free to open an issue on the [GitHub](https://github.com/JevenDev/Gracebound/issues) repo.

![roadmap](https://cdn.modrinth.com/data/cached_images/04825ea0e2e5462ffa075e783ca38b0c63a36d34.png)

## Version and Loader

- ✅ **NeoForge 1.21.1** [Active development]
- ⛔ **NeoForge 1.20.1** [Not planned]
- ⛔ **Forge 1.21.1** [Not planned]
- 🚧 **Forge 1.20.1** [Planned port]
- 🚧 **Fabric 1.21.1** [Planned port]
- 🚧 **Fabric 1.20.1** [Planned port]

## Planned Features

- More visual polish and customization for the guidance trail
- More map/minimap compatibility support for mods like Map Atlases/JourneyMap (if demand is there)
- More optional server-side controls for pack/server owners
- More tuning for how guidance behaves near its destination
- API for adding custom destinations, entity targeting, and structure targeting. Good for mod developers/modpack creators.

![credits & license](https://cdn.modrinth.com/data/cached_images/5fd3ad80e342e6985dd6ebda1f7afd9c48749fce.png)

## License

This project is licensed under the **[GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html)**.

Feel free to use this mod in modpacks, videos, etc. Just provide a link back to this page if possible :)

Looking to port the mod to your favourite loader/version outside of my scope? Feel free to, and let me know so I can add a sub-section to direct users to it!

For any general queries/unlisted questions, DM me on Twitter (@prodbyjvn) / Discord (ijvn).

<div align="center">
  
  <p><strong>⚠ <em>This mod ONLY exists on Modrinth & CurseForge as of April 2026. Any sites hosting this mod outside of Modrinth/CurseForge are not official releases.</em> ⚠</strong></p>
  
</div>
