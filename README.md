# SafeExpose

SafeExpose is a utility that enables you to expose a local application through a ScaleGuard server securely. It allows you to configure, start, and manage SSH tunnels effortlessly.

## Features
- Securely expose local applications via ScaleGuard
- Store and manage configuration settings
- Automate SSH tunnel creation and deletion
- Validate user credentials with ScaleGuard authentication

## Prerequisites
- Java 8 or higher
- A valid ScaleGuard account
- Network access to the ScaleGuard server
- Apache Maven installed

## Installation
1. Clone the repository or download the `TunnelManager.java` file.
2. Build the project and generate the JAR file using Maven:
   ```sh
   mvn clean install
   ```
   This will generate `safeexpose.jar` in the `target/` directory.
3. Run the program using Java:
   ```sh
   java -jar target/safeexpose.jar <command> [options]
   ```

## Usage
### Configure ScaleGuard Credentials
Before starting a tunnel, you need to configure your ScaleGuard server credentials:
```sh
java -jar target/safeexpose.jar configure <server-host> <username> <password>
```
Alternatively, run:
```sh
java -jar target/safeexpose.jar configure
```
This will prompt you to enter the server host, username, and password interactively.

### Show Current Configuration
To view the stored ScaleGuard configuration:
```sh
java -jar target/safeexpose.jar show
```

### Start a Tunnel
To start a tunnel for a local application:
```sh
java -jar target/safeexpose.jar start <app-name> <port>
```
Example:
```sh
java -jar target/safeexpose.jar start my-app 8080
```
This will create an SSH tunnel and expose `localhost:8080` through the ScaleGuard server.

### Delete a Tunnel
To delete a previously created tunnel:
```sh
java -jar target/safeexpose.jar delete <app-name>
```
Example:
```sh
java -jar target/safeexpose.jar delete my-app
```

## Configuration File
The configuration is stored in the user's home directory:
```
~/.tunnel_config
```
It contains:
```
server=<ScaleGuard Server URL>
username=<Your Username>
password=<Your Password>
```

## Error Handling
- If authentication fails, re-run the `configure` command to update credentials.
- Ensure the ScaleGuard server is reachable before starting a tunnel.
- Check that the specified local port is available.

## License
This project is licensed under the MIT License.

