package org.ff4j.dynamodb.property;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import org.ff4j.exception.PropertyAlreadyExistException;
import org.ff4j.exception.PropertyNotFoundException;
import org.ff4j.property.Property;
import org.ff4j.property.store.AbstractPropertyStore;
import org.ff4j.utils.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import static org.ff4j.dynamodb.DynamoDBConstants.PROPERTY_TABLE_NAME;

/*
 * #%L
 * ff4j-store-aws-dynamodb
 * %%
 * Copyright (C) 2013 - 2016 FF4J
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

/**
 * Implementation of {@link org.ff4j.property.store.PropertyStore} using Amazon DynamoDB.<br />
 *
 * To get it running, a DynamoDB table is required.
 * Either you let FF4J create it for you (use the ff4j-dynamodb.properties file to setup table name and billing options) or
 * you create it on your own.</p>
 * <p>If you choose to let FF4J create the table, you can use the following properties in the ff4j-dynamodb.properties file:<br/>
 * ff4j.store.dynamodb.property.table.name={string}<br/>
 * ff4j.store.dynamodb.property.table.billing=[PROVISIONED|PAY_PER_REQUEST]<br/>
 * ff4j.store.dynamodb.property.table.billing.rcu={int}<br/>
 * ff4j.store.dynamodb.property.table.billing.wcu={int}</p>
 * <p>If you create it on your own, you must keep the same attribute names, but you can change the table name, index name,
 * billing mode and throughput. Example:</p>
 * <code>
 *     aws dynamodb create-table --cli-input-json file://create-property-dynamodb-table.json
 * </code>
 * <p>
 *     With the following content in create-property-dynamodb-table.json file:
 *     <code>
 *         {
 *     "TableName": "ff4jproperties",
 *     "AttributeDefinitions": [
 *         {
 *             "AttributeName": "name",
 *             "AttributeType": "S"
 *         }
 *     ],
 *     "KeySchema": [
 *         {
 *             "AttributeName": "name",
 *             "KeyType": "HASH"
 *         }
 *     ],
 *     "BillingMode": "PROVISIONED",
 *     "ProvisionedThroughput": {
 *         "ReadCapacityUnits": 4,
 *         "WriteCapacityUnits": 4
 *     }
 * }
 *     </code>
 * </p>
 * <p>If you want to get more control on the connection to Amazon DynamoDB, use the appropriate constructor:<ul>
 *     <li>{@link #PropertyStoreDynamoDB(AmazonDynamoDB)}</li>
 * </ul></p>
 * @author <a href="mailto:jeromevdl@gmail.com">Jerome VAN DER LINDEN</a>
 */
public class PropertyStoreDynamoDB extends AbstractPropertyStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyStoreDynamoDB.class);

    /**
     * Internal DynamoDB client
     */
    private PropertyDynamoDBClient dynamoDBClient;

    /************************************************************************************************************/
    /**                                           CONSTRUCTORS                                                  */
    /************************************************************************************************************/

    /**
     * Default constructor using default DynamoDB client and default table name.
     * If you need more control on AWS connection (credentials, proxy, ...), use {@link #PropertyStoreDynamoDB(AmazonDynamoDB)}
     */
    public PropertyStoreDynamoDB() {
        this(AmazonDynamoDBClientBuilder.defaultClient(), PROPERTY_TABLE_NAME);
    }

    /**
     * Constructor using default DynamoDB client and custom table name.
     * If you need more control on AWS connection (credentials, proxy, ...), use {@link #PropertyStoreDynamoDB(AmazonDynamoDB, String)}
     *
     * @param tableName name of the table to use in DynamoDB
     * @deprecated use ff4j-dynamodb.properties to specify the table name
     */
    @Deprecated
    public PropertyStoreDynamoDB(String tableName) {
        this(AmazonDynamoDBClientBuilder.defaultClient(), tableName);
        LOGGER.warn("Constructor deprecated, you should use ff4j-dynamodb.properties instead");
    }

    /**
     * Constructor using custom DynamoDB client and default table name.
     *
     * @param amazonDynamoDB Amazon DynamoDB client
     */
    public PropertyStoreDynamoDB(AmazonDynamoDB amazonDynamoDB) {
        this(amazonDynamoDB, PROPERTY_TABLE_NAME);
    }

    /**
     * Constructor using custom DynamoDB client and table name.
     *
     * @param amazonDynamoDB Amazon DynamoDB client
     * @param tableName      name of the table to use in DynamoDB
     * @deprecated use ff4j-dynamodb.properties to specify the table name
     */
    @Deprecated
    public PropertyStoreDynamoDB(AmazonDynamoDB amazonDynamoDB, String tableName) {
        initStore(amazonDynamoDB, tableName);
        LOGGER.warn("Constructor deprecated, you should use ff4j-dynamodb.properties instead");
    }

    /************************************************************************************************************/
    /**                                              PUBLIC                                                     */
    /************************************************************************************************************/

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existProperty(String name) {
        try {
            getClient().getItem(name);
        } catch (PropertyNotFoundException e) {
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void createProperty(Property<T> property) {
        Util.assertNotNull(property);
        Util.assertHasLength(property.getName());
        if (existProperty(property.getName())) {
            throw new PropertyAlreadyExistException(property.getName());
        }

        getClient().put(property);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateProperty(String name, String newValue) {
        Util.assertHasLength(name);

        // read property and assign value to check if types are compatible before updating
        Property<?> property = readProperty(name);
        property.setValueFromString(newValue);

        getClient().updateProperty(name, newValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Property<?> readProperty(String name) {
        return getClient().get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteProperty(String name) {
        Util.assertHasLength(name);
        if (!existProperty(name)) {
            throw new PropertyNotFoundException(name);
        }
        getClient().deleteItem(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Property<?>> readAllProperties() {
        return getClient().getAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> listPropertyNames() {
        return getClient().getAllNames();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        getClient().deleteTable();
        createSchema();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSchema() {
        if (!getClient().tableExists()) {
            getClient().createTable();
        }
    }

    /************************************************************************************************************/
    /**                                              PRIVATE                                                    */
    /************************************************************************************************************/

    /**
     * Initialize internal dynamoDB client and create DynamoDB table if necessary
     *
     * @param amazonDynamoDB dynamoDB client
     * @param tableName      name of the table in DynamoDB
     */
    @SuppressWarnings("deprecation")
    private void initStore(AmazonDynamoDB amazonDynamoDB, String tableName) {
        dynamoDBClient = new PropertyDynamoDBClient(amazonDynamoDB, tableName);
        createSchema();
    }

    /**
     * Getter accessor for attribute 'dynamoDBClient'.
     *
     * @return current value of 'dynamoDBClient'
     */
    private PropertyDynamoDBClient getClient() {
        return dynamoDBClient;
    }
}
