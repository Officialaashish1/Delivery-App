from django.urls import path

from .. import views

urlpatterns = [
    path("summary/", views.PartnerFinanceSummaryView.as_view(), name="partner-finance-summary"),
    path("lines/", views.PartnerSettlementLineListView.as_view(), name="partner-settlement-lines"),
    path("settlements/", views.PartnerSettlementListView.as_view(), name="partner-settlements"),
    path("statement.csv", views.PartnerStatementCSVView.as_view(), name="partner-statement-csv"),
]
