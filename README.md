# GridCapa task manager application
[![Actions Status](https://github.com/farao-community/gridcapa-task-manager/workflows/CI/badge.svg)](https://github.com/farao-community/gridcapa-task-manager/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=farao-community_gridcapa-task-manager&metric=coverage)](https://sonarcloud.io/component_measures?id=farao-community_gridcapa-task-manager&metric=coverage)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=farao-community_gridcapa-task-manager&metric=alert_status)](https://sonarcloud.io/dashboard?id=farao-community_gridcapa-task-manager)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
[![Join the community on Spectrum](https://withspectrum.github.io/badge/badge.svg)](https://spectrum.chat/farao-community)

This repository contains the task manager for GridCapa. It is dedicated to monitor process tasks status and input/output files.

## Build application

Application is using Maven as base build framework. Application is simply built with following command.

```bash
mvn install
```

## Build docker image

For building Docker image of the application, start by building application.

```bash
mvn install
```

Then build docker image

```bash
docker build -t farao/gridcapa-task-manager .
```
