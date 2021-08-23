# com-utils

[![](https://github.com/khaeng/com-utils/releases/tag/1.0?label=Release)](https://github.com/#khaeng/com-utils)

Example Gradle project producing a single jar. Uses the `maven` plugin to publish the jar to the local repository.

[https://github.com/#khaeng/com-utils](https://github.com/#khaeng/com-utils)

To install the library add: 
 
   ```gradle
   repositories { 
        jcenter()
        maven { url "https://jitpack.io" }
   }
   dependencies {
         implementation 'com.github.khaeng:com-utils:1.0'
   }
   ```  



To view log when you compile your project using import this tag:
 ```
 https://jitpack.io/com/github/{Git-Username}/{Git-Repository}/{Tag or Commit-no or master-SNAPSHOT}/build.log
 https://jitpack.io/com/github/khaeng/com-utils/1.0/build.log
 ```

You can see build log-file and results to here:
 ```
 https://jitpack.io/com/github/khaeng/com-utils/1.0/
 ```

Because of Jitpack supported limitation that you can compile with gradle 5.6.x or 6.3 when you using spring-boot library.

