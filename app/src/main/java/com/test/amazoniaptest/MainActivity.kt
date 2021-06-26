package com.test.amazoniaptest

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import com.test.amazoniaptest.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    val parentSKU = "com.amazon.sample.iap.subscription.mymagazine"
    private lateinit var handler: Handler

    //Define UserId and MarketPlace
    private var currentUserId: String? = null
    private var currentMarketplace: String? = null

    private lateinit var binding: ActivityMainBinding

    private var purchasingListener: PurchasingListener = object : PurchasingListener {

        /**
         * This method recovers the user ID and the reference marketplace of the user. In case
         * we can't retrieve this information, or the callback can't be executed, out code should
         * handle those conditions and fail gracefully (we won't cover the implementation detail
         * in this workshop, however).
         */
        override fun onUserDataResponse(response: UserDataResponse) {
            Log.d("KAKA", "onUserDataResponse")
            val status = response.requestStatus
            when (status!!) {
                UserDataResponse.RequestStatus.SUCCESSFUL -> {
                    Log.d("KAKA", "onUserDataResponse SUCCESSFUL")
                    currentUserId = response.userData.userId
                    currentMarketplace = response.userData.marketplace
                }
                UserDataResponse.RequestStatus.FAILED, UserDataResponse.RequestStatus.NOT_SUPPORTED -> {
                    Log.d("KAKA", "onUserDataResponse FAILED")
                }
            }
        }

        /**
         * Allow us to get information and details about all IAP items in the catalog and all unavailable
         * SKUs. This comes in handy if we want to strike through specific IAP items in our app UI
         */

        override fun onProductDataResponse(productDataResponse: ProductDataResponse) {
            Log.d("KAKA", "onProductDataResponse")
            when (productDataResponse.requestStatus) {
                ProductDataResponse.RequestStatus.SUCCESSFUL -> {
                    Log.d("KAKA", "onProductDataResponse SUCCESSFUL")
                    //get information for all IAP Items (parent SKUs)
                    val products = productDataResponse.productData
                    for (key: String in products.keys) {
                        val product: Product = products[key]!!
                        Log.v(
                            "Product:",
                            String.format(
                                "Product: %s\n Type: %s\n SKU: %s\n Price: %s\n Description: %s\n",
                                product.title,
                                product.productType,
                                product.sku,
                                product.price,
                                product.description
                            )
                        )
                    }
                    //get all unavailable SKUs
                    for (s: String in productDataResponse.unavailableSkus) {
                        Log.v("Unavailable SKU:$s", "Unavailable SKU:$s")
                    }

                }
                ProductDataResponse.RequestStatus.FAILED -> {
                    Log.d("KAKA", "onProductDataResponse FAILED")
                }
            }
        }

        /**
         * This method is triggered only when the user makes a specific purchase for
         * the first time. If the purchase is successful, then we can fulfill the item
         * to the customer using PurchasingService.notifyFulfillment(). This would trigger
         * the onPurchaseUpdateResponse() callback, and that's the last component that
         * we need to implement.
         */
        override fun onPurchaseResponse(purchaseResponse: PurchaseResponse) {
            Log.d("KAKA", "onPurchaseResponse")
            when (purchaseResponse.requestStatus) {
                PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                    Log.d("KAKA", "onPurchaseResponse SUCCESSFUL")
                    PurchasingService.notifyFulfillment(
                        purchaseResponse.receipt.receiptId,
                        FulfillmentResult.FULFILLED
                    )
                }
                PurchaseResponse.RequestStatus.FAILED -> {
                    Log.d("KAKA", "onPurchaseResponse FAILED")
                }
            }
        }

        /**
         * Fulfilling the item to the user.
         *
         * In order to fulfill the item to the user, we need
         * to do it in this function. That's because we would fulfill the subscription anytime
         * the user logs in, if the receipt is still valid.
         * If the status of the Request is successful, it means this user has the right to access
         * the item (in this case, the subscription).
         * We iterate through all the receipts for the user, and if the receipt is not cancelled, we
         * can send the message "Subscribed" to the handler, which will update the UI accordingly.
         *
         */
        override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
            Log.d("KAKA", "onPurchaseUpdatesResponse")
            when (response.requestStatus) {
                PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL -> {
                    Log.d("KAKA", "onPurchaseUpdatesResponse SUCCESSFUL size = " + response.receipts.size)
                    // So if receipts size is empty here, it means that user is not VIP user, otherwise they are VIP user
                    for (receipt in response.receipts) {
                        // Process receipts
                        if (!receipt.isCanceled) {
                            val m = Message()
                            m.obj = "Subscribed"
                            handler.handleMessage(m)
                        }
                    }
                    if (response.hasMore()) {
                        PurchasingService.getPurchaseUpdates(true)
                    }
                }
                PurchaseUpdatesResponse.RequestStatus.FAILED -> {
                    Log.d("KAKA", "onPurchaseUpdatesResponse FAILED")
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Register purchase listener
        PurchasingService.registerListener(this, purchasingListener)

        //create a handler for the UI changes
        handler = object : Handler() {

            override fun handleMessage(msg: Message) {
                // Your logic code here.
                if (msg.obj.equals("Subscribed")){
                    binding.textView.text = "SUBSCRIBED"
                    binding.textView.setTextColor(Color.RED)
                }
            }
        }

        setOnClickListeners()

    }

    private fun setOnClickListeners() {
        // When click this button, the amazon app UI will appear.
        // This is NOT the UI that end-customer will see when the app
        // is published on the Amazon Appstore. This is a test UI
        // provided by the Amazon App Tester that allows us to test
        // the transactions
        binding.subscriptionButton.setOnClickListener {
            PurchasingService.purchase(parentSKU)
        }
    }

    override fun onResume() {
        super.onResume()
        //getUserData() will query the Appstore for the Users information
        PurchasingService.getUserData()

        //getPurchaseUpdates() will query the Appstore for any previous purchase
        PurchasingService.getPurchaseUpdates(true)

        //getProductData will validate the SKUs with Amazon Appstore
        val productSkus = HashSet<String>()
        productSkus.add(parentSKU)
        PurchasingService.getProductData(productSkus)
    }

}