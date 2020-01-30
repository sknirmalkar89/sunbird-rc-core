let Functions = require("./workflow/Functions");
const _ = require('lodash')
const async = require('async');
const entityType = 'Employee';
const appConfig = require('./sdk/appConfig');
const RegistryService = require('./sdk/RegistryService')
const KeycloakHelper = require('./sdk/KeycloakHelper');
const httpUtils = require('./sdk/httpUtils.js');
const logger = require('./sdk/log4j');
const vars = require('./sdk/vars').getAllVars(process.env.NODE_ENV);
const nerKeycloakHelper = new KeycloakHelper(vars.keycloak_ner);
var registryService = new RegistryService();


class EPRFunctions extends Functions {
    EPRFunctions() {
        setRequest(undefined)
    }

    getAdminUsers(callback) {
        this.getUsersByRole('admin', (err, data) => {
            this.addEmailToPlaceHolder(data, callback);
        });
    }

    getPartnerAdminUsers(callback) {
        this.getUsersByRole('partner-admin', (err, data) => {
            this.addEmailToPlaceHolder(data, callback);
        })
    }

    getFinAdminUsers(callback) {
        this.getUsersByRole('fin-admin', (err, data) => {
            this.addEmailToPlaceHolder(data, callback);
        });
    }

    getReporterUsers(callback) {
        this.getUsersByRole('reporter', (err, data) => {
            this.addEmailToPlaceHolder(data, callback);
        });
    }

    getOwnerUsers(callback) {
        this.getUsersByRole('owner', (err, data) => {
            this.addEmailToPlaceHolder(data, callback);
        });
    }

    getRegistryUsersMailId(callback) {
        this.getUserByid((err, data) => {
            if (data) {
                this.addEmailToPlaceHolder([data.result[entityType]], callback);
            }
        })
    }

    addEmailToPlaceHolder(data, callback) {
        this.addToPlaceholders('emailIds', _.map(data, 'email'));
        callback();
    }

    sendNotificationForRequestToOnBoard(callback) {
        this.addToPlaceholders('subject', "Request to Onboard " + this.request.body.request[entityType].name)
        this.addToPlaceholders('templateId', "requestOnboardTemplate");
        let tempParams = this.request.body.request[entityType];
        tempParams['employeeName'] = this.request.body.request[entityType].name
        tempParams['empRecord'] = "http://localhost:9082/profile/" + JSON.parse(this.response).result[entityType].osid
        this.addToPlaceholders('templateParams', tempParams);
        let actions = ['getAdminUsers', 'sendNotifications'];
        this.invoke(actions, (err, data) => {
            callback(null, data)
        });
    }

    sendOnboardSuccesNotification(callback) {
        this.addToPlaceholders('subject', "New Employee Onboarded")
        this.addToPlaceholders('templateId', "newPartnerEmployeeTemplate");
        let actions = ['getRegistryUsersInfo', 'getAdminUsers', 'sendNotifications', 'getReporterUsers', 'sendNotifications'];
        this.invoke(actions, (err, data) => {
            callback(null, data)
        });
    }

    getRegistryUsersInfo(callback) {
        let tempParams = {}
        this.getUserByid((err, data) => {
            if (data) {
                tempParams = data.result[entityType];
                tempParams['employeeName'] = data.result[entityType].name
                tempParams['eprURL'] = "http://localhost:9082"
                this.addToPlaceholders('templateParams', tempParams)
                callback()
            }
        })
    }

    notifyUsersBasedOnAttributes(callback) {
        let params = _.keys(this.request.body.request[entityType]);
        async.forEachSeries(this.attributes, (value, callback2) => {
            if (_.includes(params, value)) {
                let params = {
                    paramName: value,
                    paramValue: this.request.body.request[entityType][value]
                }
                this.addToPlaceholders('templateParams', params)
                this.getActions(value, (err, data) => {
                    if (data) {
                        callback2();
                    }
                });
            } else {
                callback2();
            }
            if (count === this.attributes.length) {
                callback(null, "success")
            }
        });
    }

    getActions(attribute, callback) {
        let actions = []
        switch (attribute) {
            case 'githubId':
                actions = ['getFinAdminUsers', 'sendNotifications'];
                this.addToPlaceholders('templateId', "updateParamTemplate");
                this.invoke(actions, (err, data) => {
                    callback(null, data)
                });
                break;
            case 'macAddress':
                actions = ['getReporterUsers', 'sendNotifications'];
                this.addToPlaceholders('subject', "MacAdress updated");
                this.addToPlaceholders('templateId', "macAddressUpdateTemplate");
                this.invoke(actions, (err, data) => {
                    callback(null, data)
                });
                break;
            case 'isOnboarded':
                actions = ['getRegistryUsersMailId', 'getRegistryUsersInfo', 'sendNotifications'];
                this.addToPlaceholders('templateId', "onboardSuccessTemplate");
                this.addToPlaceholders('subject', "Successfully Onboarded to EkStep");
                this.invoke(actions, (err, data) => {
                    callback(null, data)
                });
                break;
        }
    }

    isIlimiUser(callback) {
        let req = _.cloneDeep(this.request);
        req.body.id = appConfig.APP_ID.READ;
        registryService.readRecord(req, function (err, data) {
            if (data && data.params.status === appConfig.STATUS.SUCCESSFULL) {
                if (data.result[entityType].orgName === 'ILIMI')
                    callback(null, data);
                else
                    callback(new Error("record does not belongs to the ILIMI org"))
            } else
                callback(err)
        });
    }

    getNERToken(readResponse, callback) {
        nerKeycloakHelper.getToken((err, token) => {
            if (token) callback(null, readResponse, token.access_token.token);
            else callback(err)
        });
    }

    getNERid(readResponse, token, callback) {
        const headers = {
            'content-type': 'application/json',
            authorization: "Bearer " + token
        }
        const searchReq = {
            body: {
                id: appConfig.APP_ID.SEARCH,
                request: {
                    entityType: [entityType],
                    filters: { email: { eq: readResponse.result[entityType].email } }
                }
            },
            headers: headers,
            url: vars.nerUtilServiceUrl + appConfig.UTILS_URL_CONFIG.SEARCH
        }
        httpUtils.post(searchReq, (err, res) => {
            if (res.body.params.status === appConfig.STATUS.SUCCESSFULL && res.body.result[entityType].length > 0) {
                callback(null, res.body, headers);
            } else if (res.body.result[entityType].length <= 0) {
                callback("error: record is not present in the client regitry");
            }
        });
    }

    notifyNER(searchRes, headers, callback) {
        let updateReq = _.cloneDeep(this.request);
        updateReq.body.request[entityType].osid = searchRes.result[entityType][0].osid;
        let option = {
            body: updateReq.body,
            headers: headers,
            url: vars.nerUtilServiceUrl + appConfig.UTILS_URL_CONFIG.NOTIFICATIONS
        }
        httpUtils.post(option, (err, res) => {
            if (res)
                callback(null, res.body);
            else
                callback(err)
        });
    }

    /**
     * 
     * @param {*} req 
     */
    updateRecordOfClientRegistry() {
        if (JSON.parse(this.response).params.status === appConfig.STATUS.SUCCESSFULL) {
            logger.debug("updating record in client registry started", this.request.body)
            async.waterfall([
                this.isIlimiUser.bind(this),
                this.getNERToken.bind(this),
                this.getNERid.bind(this),
                this.notifyNER.bind(this)
            ], (err, result) => {
                if (result)
                    logger.debug("Updating record in client registry is completed", result);
                else
                    logger.debug(err)
            });
        }
    }

    invoke(actions, callback) {
        if (actions.length > 0) {
            let count = 0;
            async.forEachSeries(actions, (value, callback2) => {
                count++;
                this[value]((err, data) => {
                    callback2()
                });
                if (count == actions.length) {
                    callback(null, actions);
                }
            });
        }
    }

    searchCheck(callback) {
        console.log("search is hit")
        callback(null)
    }

}

module.exports = EPRFunctions