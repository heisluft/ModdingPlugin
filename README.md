# ClassicModding
A gradle plugin suite for developing Mods for Minecraft Classic, Indev and Infdev

`I am in no way affiliated with Mojang or Microsoft, all rights to the Minecraft game and branding
belong to their respective owners.`
## How to use this
This Repo defines two plugins, only one of which shall be explained here:
The JarModDev Plugin, which, using Mappings I already crafted, allows to
easily generate Patches as well as AccessTransformers and Plugins for MML.
It requires Gradle 8.8. 

Into your `settings.gradle`, put this:
```groovy
pluginManagement {
  repositories {
    maven {
      url 'https://heisluft.de/maven/'
    }
    maven {
      url 'https://maven.minecraftforge.net/'
    }
  }
}
```
Then, into your `build.gradle`, put this:
```groovy
plugins {
 id 'de.heisluft.modding.classic.jarmoddev' version '0.3.0-pre+65'
}

...

classicMC {
  version = mcVersion
  mappingType "type" // either 'fergie' or 'source' 
}
```
This will already give you the toolchain you need for working with mc. Including applying patches to guarantee recompilation
### Explainations
- The "mappingType" property specifies the level of applied deobfuscation, each with their own merits:

    | Mapping  | Description                                                                                     |
    |----------|-------------------------------------------------------------------------------------------------|
    | 'fergie' | Partial mappings. Class names are deobfuscated, Method and Field names are named systematically |
    | 'source' | Complete deobfuscation (excluding Parameters and Local variables)                               |
    Fergie Mappings allow for an interchangeable naming scheme as source mapping can be altered, enabling (theoretically)
    for multiple MML services to use them for lookups as a sort of "lingua franca". Patches are always generated with these
    intermediary mappings, since all Members are named systematically, remapping patches does not require parsing of any kind,
    only String replacement.

    Source Mappings, on the other Hand allow you to easily make sense of what
    the game is doing, so the best way to go about modding would be:
  1. use 'source' mappings to figure out the parts you want to change
  2. swap to 'fergie' mappings, pasting your patch back in
  3. rename the required members to fergie mappings
  4. generate the fergie patches.

    It is planned to have a sort of parsing System allowing for the automatic generation of 'fergie' mapped patches from 'source'
    mapped, edited code files.

- All game versions available are supported. If you have got a missing version, feel free to contact me.
- For generating your mc source code use the task 'regenSrc'.
**Be Careful:** `It will wipe out your previously made changes, save your Patches!`
- For generating a run config use 'genBSLRun', this will automatically register a run config for IntelliJ. The config may be added to via task config:
    ```groovy
    genBSLRun {
        jvmArgs.addAll "myArgs"
        appArgs.add "myAppArg"
    }
    ```
- You will need to add all your sourceSets to the classpath manually. This can be done by configuring the `makeCPFile` task:
    ```groovy
    makeCPFile {
        paths.addAll sourceSets.getByName("main").output.classesDirs
        paths.add sourceSets.getByName("main").output.resourcesDir
    }
    ```
## History
This plugin started out as an attempt to streamline my many .sh files for working with Minecraft
Classc into a more mature Toolchain, making it easy to
start mapping attempts for a diverse version range.

I originally started decompiling and deobfuscating old Minecraft versions
as a Real Life test case for my Deobfuscation Tool Library, initially i used LegacyLancher
from the forge team for launching the game, but soon I had a
much more outlandish idea: How about launching with McModLauncher, on a
JPMS enabled configuration?

## Shoutouts
- Mojang, and Notch for creating this beautiful game
- cpw et al for creating LegacyLauncher and ModLauncher
- The MinecraftForge team for creating Artifactural.