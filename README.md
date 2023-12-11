# Restate Protobuf

This repo contains the Restate Protobuf contracts for definining Restate services, core types and built-in services.

You can check it out from BSR: https://buf.build/restatedev/proto

For the service protocol contracts, check https://github.com/restatedev/service-protocol

## Releasing

You need to publish a new release to BSR:

```shell
buf push
```

You may need to login first with `buf registry login`.

Then create the release manually on the Github releases page: https://github.com/restatedev/proto/releases
