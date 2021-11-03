## AWS Fleet Provisioning Plugin

This plugin is meant to run with [aws-greengrass-nucleus](https://github.com/aws-greengrass/aws-greengrass-nucleus) 
as a device provisioning plugin. The plugin provides the ability to provision a Greengrass device using 
[AWS FleetProvisioning Service](https://docs.aws.amazon.com/iot/latest/developerguide/provision-wo-cert.html). 

## Setup before running the plugin
Following setup is required in the cloud before the plugin can run successfully
- Create provisioning template in the AWS account that is to be used for the device
- Create claim certificate for the provisioning template

The plugin requires following artifacts on the device before starting greengrass with plugin
- Claim certificate for the provisioning template at a secure path
- FleetProvisioningByClaim.jar file at a secure path
- config.yaml with the plugin parameters which will be used to initialize greengrass

## Parameters
This plugin takes following parameters

### Required
- **provisioningTemplate**: The provisioning template name
- **claimCertificatePath**: Path of the claim certificate on the device.
- **claimCertificatePrivateKeyPath**: Path of the claim certificate private key on the device
- **rootCaPath**: Path of the root CA
- **iotDataEndpoint**: IoT data endpoint for the AWS account
- **rootPath**: Root path for Greengrass

### Optional
- **deviceId**: The device identifier which will be used as client id in the mqtt connection to AWS IoT
- **templateParameters**: Map<String, String> of parameters which will be passed to provisioning template 
- **awsRegion**: AWS Region
- **iotCredentialEndpoint**: IoT credentials endpoint for the AWS account
- **iotRoleAlias**: Role alias to be used by Greengrass nucleus to get TES credentials.
- **proxyUrl**: Http proxy url to be used for mqtt connection. The url is of format
  *scheme://userinfo@host:port* 
    - scheme – The scheme, which must be http or https.
    - userinfo – (Optional) The user name and password information. If you specify this in the url, the Greengrass core device ignores the username and password fields.
    - host – The host name or IP address of the proxy server.
    - port – (Optional) The port number. If you don't specify the port, then the Greengrass core device uses the following default values:
        - http – 80
        - https – 443
    
- **proxyUserName:** The user name to use to authenticate to the proxy server.
- **proxyPassword:** The password to use to authenticate to the proxy server.

## Command to start greengrass with plugin

Use following command to start greengrass with provisioning plugin on the device

`sudo -E java -Dlog.store=FILE -jar ./GreengrassCore/lib/Greengrass.jar 
-i <secure_path_to_config>/config.yaml 
--trusted-plugin <secure_path_to_plugin_jar>/FleetProvisioningByClaim.jar 
-r /home/ec2-user/demo/greengrass/v2`


## License
This project is licensed under the Apache-2.0 License.

