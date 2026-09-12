from django.urls import path

from .views import NotificationListView, NotificationReadAllView, NotificationReadView, NotificationUnreadCountView

urlpatterns = [
    path("", NotificationListView.as_view(), name="notifications"),
    path("unread-count/", NotificationUnreadCountView.as_view(), name="notifications_unread"),
    path("read-all/", NotificationReadAllView.as_view(), name="notifications_read_all"),
    path("<int:pk>/read/", NotificationReadView.as_view(), name="notification_read"),
]