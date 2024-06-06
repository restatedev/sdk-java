# Releasing sdk-java

To release sdk-java:

* First make sure `service-protocol` and `proto` subtrees are updated to the latest version. See the main [README.md](../README.md#contributing)
* Change the version to the desired version using the [Bump version workflow](https://github.com/restatedev/sdk-java/actions/workflows/bump.yaml).
* Merge the auto generated PR
* Wait for CI on main to execute the release
* Create the Github Release manually
* Create the release branch if the release is a MAJOR or MINOR with the name `release-MAJOR.MINOR`
* Change the version again to the next `-SNAPSHOT` version (e.g. from `0.4.0` to `0.5.0-SNAPSHOT`, from `0.4.1` to `0.5.0-SNAPSHOT`)
* Merge the auto generated PR
* Update the sdk-java version the e2e tests is using to the new snapshot: https://github.com/restatedev/e2e/blob/main/gradle/libs.versions.toml
