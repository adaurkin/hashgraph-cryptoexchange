# Decentralized Matching Engine Testing on Hashgraph
A basic CryptoExchange app based on a public domain demo app CryptocurrencyDemo from [Swirlds SDK 17.09.15 (Alpha Version)](http://www.swirlds.com/download/).
Developed for Lykke Streams contest [Decentralized Matching Engine Testing on Hashgraph (beta)](https://streams.lykke.com/Project/ProjectDetails/decentralized-realtime-matching-engine).

## How to run the app
1. Compile the app:
- [from the command line](https://hashgraph.com/dev/installcli/)
or
- [in Eclipse](https://hashgraph.com/dev/installeclipse/)

2. Make a config file
Use config.txt from this repo as a template. List all your nodes in lines starting with `address`. Then put config.txt near swirlds.jar.
See config.txt from the SDK for more info.
Any spreadsheet app can help managing big config files as CSV.

3. Run the app in the Swirlds browser ([how-to](https://hashgraph.com/dev/runbrowser/))
`java -jar swirlds.jar`
Remote nodes can be started with `ssh -i 'your-key.pem' user@host 'java -jar swirlds.jar'`
