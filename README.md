# Description

The Cardano Blockchain Pack integrates Cardano with Joget, and allows you to design Joget apps that interacts with the Cardano blockchain.

Do see [documentation and sample app](https://dev.joget.org/community/display/marketplace/Cardano+Blockchain+Pack).

> :warning: **This project is in early development stages and not ready for production use.**

Credits: [https://github.com/bloxbean/cardano-client-lib](https://github.com/bloxbean/cardano-client-lib)

# Changelog

## Q1 2023 (In Progress)
#### Added
- Updated cardano client library to 0.4.2
- Added support to use preprod network
- Changed default explorer to Cardanoscan

#### Fixed
- Fixed support for Koios backend
- Removed cardano native explorer

## Q4 2022
#### Added
- Changed Account Base Address to an editable field (Credits: @Farheen2003)

#### Fixed
- Update cardano client lib for networks support
- Fixed method throws incompatibility with Joget multi-tenant

## Q3 2022
#### Added
- Cardano Account Load Binder - Added support to load native asset balances
- Added new Form Element plugin - Cardano Explorer Link
- Cardano Burn Token Tool - Simplify plugin configurations
- Added convenient helplinks to documentation for all plugins
- Cardano Explorer Link - Support checking policy, address, asset on explorers
- Support Vasil HF
- Transactions - Support setting maximum fee limit
- Cardano Send Transaction Tool - Simplify configs for multiple receivers mode
- Temporary workaround for new testnet endpoints (PREVIEW & PREPROD)
- Codebase refactoring
- Factorize wait tx logic

#### Fixes
- Cardano Explorer Link - Fixed NPE when edit element in Form Builder
- Cardano Mint Token Tool - Fixed regression for multi-tenant setups
- Cardano Mint Token Tool - Remove unnecessary wfAssignment call
- Cardano Generate Account Tool - Fixed backend service json missing causing NPE

## Q2 2022
#### Added
- Added support for Koios backend
- Cardano Send Transaction Tool - Added support for multiple receivers (e.g.: airdropping tokens)
- Cardano Mint Token Tool - Added support for minting NFTs
- Cardano Send Transaction Tool - Support NFT transfers
- Cardano Burn Token Tool - Added support for burning NFTs
- Added flag for transaction execution success status
- Cardano Mint Token Tool - Added support for reusing minting policy
- Backend service now defaults on Koios APIs
- Cardano Mint Token Tool - Added support to append NFT properties from form data
- Send & Minting - Tx msg comply with CIP20 standard

#### Fixes
- Adapt code changes to updated cardano library
- Update mint and burn tool for future multi policy signing implementation
- Fixed transactions store bad data to workflow variable despite transaction failure
- Cardano Mint Token Tool - Fixed NFT form data properties conflict with same property labels

## Q1 2022
#### Added
- Added support for GraphQL APIs, Backend service now defaults on Dandelion GraphQL APIs
- Added support for Joget running as multi-tenant
- Cardano Send Transaction Tool - Support native token transfers
- Added new Process Tool plugin - Cardano Mint Token Tool
- Cardano Mint Token Tool - Added store minting policy to form function
- Added new Process Tool plugin - Cardano Burn Token Tool

#### Fixes
- Major codebase restructuring & refactoring
- Added additional fallbacks for Dandelion GraphQL endpoints
- Cardano Send Transaction Tool - Fixed missing asset name for native token transfers

## Q4 2021
#### Initial commit of this plugin pack, which includes:
- Cardano Send Transaction Tool
- Cardano Generate Account Tool
- Cardano Account Load Binder

#### Added
- Added new Form Load Binder plugin - Cardano Metadata Load Binder
- Cardano Send Transaction Tool - Added store transaction ID to workflow variable

#### Fixes
- Cardano Send Transaction Tool - Added more helpful error message upon transaction failure
- Updated cardano lib for Address Utxo API change fix

# Getting Help

JogetOSS is a community-led team for open source software related to the [Joget](https://www.joget.org) no-code/low-code application platform.
Projects under JogetOSS are community-driven and community-supported.
To obtain support, ask questions, get answers and help others, please participate in the [Community Q&A](https://answers.joget.org/).

# Contributing

This project welcomes contributions and suggestions, please open an issue or create a pull request.

Please note that all interactions fall under our [Code of Conduct](https://github.com/jogetoss/repo-template/blob/main/CODE_OF_CONDUCT.md).

# Licensing

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

NOTE: This software may depend on other packages that may be licensed under different open source licenses.
