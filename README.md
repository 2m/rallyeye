# RallyEye Data [![ci-badge][]][ci]

Backend function for [RallyEye][rallyeye].
Serves rally data from [RallySimFans][rallysimfans] with relaxed CORS policy.

[ci-badge]: https://github.com/2m/rallyeye-data/actions/workflows/ci.yml/badge.svg
[ci]:       https://github.com/2m/rallyeye-data/actions/workflows/ci.yml

[rallyeye]:     https://github.com/2m/rallyeye
[rallysimfans]: https://www.rallysimfans.hu

## Development

Start server locally by running:

```sh
sbt --client reStart
```

Then get specific rally data by running:

```sh
http localhost:8080/rally/52804
```
