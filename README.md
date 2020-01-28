BIOSocks
===

## Background/About this project

BIOSocks  is an socks5 tool to surf the Internet

## Requirements

Java 
Chrome & SwitchyOmega

## Usage
├── target  
│   └── biosocks-0.0.1.jar                            
├── data                          
│   ├── client.json    
│   └── server.json                  
├── client.sh  
└── server.sh                           

### Client

config.json
```json
{
  "server": "server ip", 
  "serverPort": 9501,
  "client": "0.0.0.0",
  "clientPort": 9101
}
```
start client(a local SOCKS5 proxy)
```$xslt
java -jar ./target/biosocks-0.0.1.jar client
```
### Server

server.json
```json
{
  "server": "0.0.0.0",
  "serverPort": 9501
}

```
start server
```
java -jar ./target/biosocks-0.0.1.jar server
```
### connect proxy
use Chrome & SwitchyOmega to connect to SOCKS5 proxy



