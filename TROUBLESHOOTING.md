# Troubleshooting

## The tests fail with java.lang.NoSuchMethodError
* After updating the Kotlin version, there may be multiple copies of the compiler plugin in `lib-kotlin`, for example:

```
lib-kotlin
├───j2k-1.2.72-release-68.jar <- old version
├───j2k-1.3.11-release-272.jar
├───kotlin-plugin-1.2.72-release-68.jar <- old version
└───kotlin-plugin-1.3.11-release-272.jar
```

* The issue is that the compiler finds multiple versions of the same class on the classpath
* To fix this, simply remove the older JARs
* If that still does not work, delete the entire `lib-kotlin` folder
    * Gradle will automatically re-download the necessary files once the project is built again
