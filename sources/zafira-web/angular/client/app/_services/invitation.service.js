(function() {
    'use strict';

    angular
        .module('app.services')
        .factory('InvitationService', ['$httpMock', '$cookies', '$rootScope', '$state', 'UtilService', 'UserService', 'API_URL', InvitationService])

    function InvitationService($httpMock, $cookies, $rootScope, $state, UtilService, UserService, API_URL) {

        var service = {};

        service.invitations = [];

        service.invite = invite;
        service.retryInvite = retryInvite;
        service.getInvitation = getInvitation;
        service.getAllInvitations = getAllInvitations;
        service.deleteInvitation = deleteInvitation;

        return service;

        function invite(invitations) {
            return $httpMock.post(API_URL + '/api/invitations', invitations).then(UtilService.handleSuccess, UtilService.handleError('Failed to invite users'));
        }

        function retryInvite(invitation) {
            return $httpMock.post(API_URL + '/api/invitations/retry', invitation).then(UtilService.handleSuccess, UtilService.handleError('Failed to retry user invitation'));
        }

        function getInvitation(token) {
            return $httpMock.get(API_URL + '/api/invitations/info?token=' + token).then(UtilService.handleSuccess, UtilService.handleError('Failed to get user invitation'));
        }

        function getAllInvitations() {
            return $httpMock.get(API_URL + '/api/invitations/all').then(UtilService.handleSuccess, UtilService.handleError('Failed to get all user invitations'));
        }

        function deleteInvitation(idOrEmail) {
            return $httpMock.delete(API_URL + '/api/invitations/' + idOrEmail).then(UtilService.handleSuccess, UtilService.handleError('Failed to delete user invitation'));
        }

    }
})();
