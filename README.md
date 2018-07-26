# Delivery Demo
A demo for issuing cash to Buyer from Bank, and exchange some goods with Seller

# Characters
 - Bank, issues cash.
 - Buyer, got cash from Bank, and buy some goods from Seller with those cash.
 - Seller, exchange goods with Buyer for gotting some cash.
 - Oracle[TBD], Buyer can check does the goods have been delivered or not for it.

# Building source code

```bash
$ cd deliverydemo
$ ./gradlew clean
$ ./gradlew deployNodesJava -Poffline=trueexit
```

# Services starting up

```bash
$ ./build/nodes/runnodes
```
# Business Flow
 * Bank issues cash to Buyer,
 ```text
 PartyA CLI
 >>> flow start TokenIssueFlow owner: PartyB, amount: 99
 ```
 * Buyer places an order with sb Seller,
 ```text
 PartyB CLI
 >>> flow start OrderPlaceFlow$Request seller: PartyC, sellingPrice: 12.9, downPayments: 0.1
 ```
 * Seller deliveres the goods to Buyer, and Buyer will check the delivere status with Oracle[TBD],
  3.1. If has been signed, Buyer will pay some cash to Seller,
  3.2. If not, ...[TBD]
 * Seller got some cash.

