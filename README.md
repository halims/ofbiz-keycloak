<<<<<<< HEAD
# ofbiz-keycloak
=======
# Ofbiz Keycloak Module

This module provides integration between Apache OFBiz and Keycloak for authentication and user management.
It is still Work In Progress, so use on your own risk.

## Features
- Keycloak authentication for OFBiz users
- User management via Keycloak
- Configurable Keycloak endpoints

## Structure
- `config/`: Keycloak configuration files
- `docs/`: Documentation and usage guides

## Build
1. copy your `ofbiz.jar` file to `$PROJECT_HOME/libs/` folder
2. run `gradle createConfigMap`
3. file created `config/keycloak-ofbiz-filter-configmap.yaml`
4. use that file for your kubernetes deployment

## Setup
1. adjust to your setting inside `keycloak-config.xml` part of configmap in `config/keycloak-ofbiz-filter-configmap.yaml`
2. install the config-map resources `config/keycloak-ofbiz-filter-configmap.yaml`.
   ```bash
   kubectl apply -n <your ofbiz namespace> -f config/keycloak-ofbiz-filter-configmap.yaml
   ```
3. update your ofbiz deployment resources, so that the pods will mount from config-map the following files:
   1. plugin jar file inside the `$OFBIZ_HOME/plugin` folder, taken from `keycloak-ofbiz-filter-0.1.0.jar` part.
   2. plugin configuration file inside `$OFBIZ_HOME/plugin` folder, taken from `ofbiz-component.xml` part.
   3. keycloak configuration file inside `$OFBIZ_HOME/config` folder, taken from `keycloak-config.xml` part.
   For example of deployment configuration, refer to `config/deployment-patch.yaml`.
4. Refer to `docs/` for integration and usage instructions (TBD).

## License
Apache License 2.0
>>>>>>> e59fb4c (Initial commit: OFBiz Keycloak integration, .gitignore, and cleanup)
