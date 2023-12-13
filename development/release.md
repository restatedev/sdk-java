# Releasing sdk-java

To release sdk-java:

* Change the version to the desired version using the [Bump version workflow](https://github.com/restatedev/sdk-java/actions/workflows/bump.yaml).
* Merge the auto generated PR
* Wait for CI on main to execute the release
* Create the Github Release manually
* Change the version again to the next `-SNAPSHOT` version (e.g. from `0.4.0` to `0.5.0-SNAPSHOT`, from `0.4.1` to `0.5.0-SNAPSHOT`)
* Merge the auto generated PR
