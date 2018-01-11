# Decentralized Matching Engine Testing on Hashgraph
A basic CryptoExchange app based on a public domain demo app CryptocurrencyDemo from [Swirlds SDK 17.09.15 (Alpha Version)](http://www.swirlds.com/download/). Developed for Lykke Streams contest [Decentralized Matching Engine Testing on Hashgraph (beta)](https://streams.lykke.com/Project/ProjectDetails/decentralized-realtime-matching-engine). Find more details and some test results in the [report](https://docs.google.com/document/d/1IhU49iBtbfn8Jf8JhD2zzVjbeKGTmv5PD1wUwE1cTKY/edit).

## How to run the app
1. Compile the app:
- [from the command line](https://hashgraph.com/dev/installcli/)<br/>
or<br/>
- [in Eclipse](https://hashgraph.com/dev/installeclipse/)

2. Make a config file

Use config.txt from this repo as a template. List all your nodes in lines starting with `address`. Then put config.txt near swirlds.jar.<br/>
See config.txt from the SDK for more info.

3. Run the app in the Swirlds browser ([how-to](https://hashgraph.com/dev/runbrowser/))

`java -jar swirlds.jar`<br/>
Remote nodes can be started with<br/>
`ssh -i 'your-key.pem' user@host 'java -jar swirlds.jar'`

---
## Some advice
- Be sure to have JDK 1.8 installed. Even 1.9 does not work for the Swirlds SDK.
- Any spreadsheet app can help managing big config files as CSV.
