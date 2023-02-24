# Javacard Maven Plugin

## Usage

```xml
<build>
    <plugins>
        <plugin>
			<groupId>com.github.ryarnyah</groupId>
			<artifactId>javacard-maven-plugin</artifactId>
			<version>${javacard-maven-plugin.version}</version>
			<configuration>
				<jcdkPath>${jc.home}</jcdkPath>
				<applets>
					<applet>
						<appletClass>MyApplet</appletClass>
						<packageName>com.github.test</packageName>
						<packageAID>${package.aid}</packageAID>
						<appletAID>${applet.aid}</appletAID>
						<outputName>my-applet</outputName>
					</applet>
				</applets>
			</configuration>
		</plugin>
    </plugins>
</build>
```
