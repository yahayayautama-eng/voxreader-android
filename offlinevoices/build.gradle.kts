plugins {
  id("com.android.asset-pack")
}

assetPack {
  packName.set("offlinevoices")
  dynamicDelivery {
    deliveryType.set("install-time")
  }
}
