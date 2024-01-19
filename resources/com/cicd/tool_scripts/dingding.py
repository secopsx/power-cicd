#!/usr/bin/python3
# -*- coding: utf-8 -*-

# --------------------------------------------------------------------------------------------
#  请把本工具【同时也】放一份到 jenkins 服务器的： /data/jenkins/tools/dingding.py
# --------------------------------------------------------------------------------------------


import re
import sys
import json
import time
import logging
import requests
import urllib
import hmac
import base64
import hashlib
import queue

_ver = sys.version_info
is_py3 = (_ver[0] == 3)

logger = logging.getLogger("dingRobot")

try:
    quote_plus = urllib.parse.quote_plus
except AttributeError:
    quote_plus = urllib.quote_plus

try:
    JSONDecodeError = json.decoder.JSONDecodeError
except AttributeError:
    JSONDecodeError = ValueError



def is_not_null_and_blank_str(content):
    """
    非空字符串
    :param content: 字符串
    :return: 非空 - True，空 - False

    >>> is_not_null_and_blank_str('')
    False
    >>> is_not_null_and_blank_str(' ')
    False
    >>> is_not_null_and_blank_str('  ')
    False
    >>> is_not_null_and_blank_str('123')
    True
    """
    if content and content.strip():
        return True
    else:
        return False


class DingtalkChatbot(object):
    """
    钉钉群自定义机器人（每个机器人每分钟最多发送20条），支持文本（text）、连接（link）、markdown三种消息类型！
    """
    def __init__(self, webhook, secret=None, pc_slide=False, fail_notice=False):
        """
        机器人初始化
        :param webhook: 钉钉群自定义机器人webhook地址
        :param secret: 机器人安全设置页面勾选“加签”时需要传入的密钥
        :param pc_slide: 消息链接打开方式，默认False为浏览器打开，设置为True时为PC端侧边栏打开
        :param fail_notice: 消息发送失败提醒，默认为False不提醒，开发者可以根据返回的消息发送结果自行判断和处理
        """
        super(DingtalkChatbot, self).__init__()
        self.headers = {'Content-Type': 'application/json; charset=utf-8'}
        self.queue = queue.Queue(20)  # 钉钉官方限流每分钟发送20条信息
        self.webhook = webhook
        self.secret = secret
        self.pc_slide = pc_slide
        self.fail_notice = fail_notice
        self.start_time = time.time()  # 加签时，请求时间戳与请求时间不能超过1小时，用于定时更新签名
        if self.secret is not None and self.secret.startswith('SEC'):
            self.update_webhook()
            
    def update_webhook(self):
        """
        钉钉群自定义机器人安全设置加签时，签名中的时间戳与请求时不能超过一个小时，所以每个1小时需要更新签名
        """
        if is_py3:
            timestamp = round(self.start_time * 1000)
            string_to_sign = '{}\n{}'.format(timestamp, self.secret)
            hmac_code = hmac.new(self.secret.encode(), string_to_sign.encode(), digestmod=hashlib.sha256).digest()            
        else:
            timestamp = long(round(self.start_time * 1000))
            secret_enc = bytes(self.secret).encode('utf-8')
            string_to_sign = '{}\n{}'.format(timestamp, self.secret)
            string_to_sign_enc = bytes(string_to_sign).encode('utf-8')
            hmac_code = hmac.new(secret_enc, string_to_sign_enc, digestmod=hashlib.sha256).digest()
        
        sign = quote_plus(base64.b64encode(hmac_code))
        if 'timestamp'in self.webhook:
            self.webhook = '{}&timestamp={}&sign={}'.format(self.webhook[:self.webhook.find('&timestamp')], str(timestamp), sign)  # 更新时间戳
        else:
            self.webhook = '{}&timestamp={}&sign={}'.format(self.webhook, str(timestamp), sign)  # 首次初始化

    def msg_open_type(self, url):
        """
        消息链接的打开方式
        1、默认或不设置时，为浏览器打开：pc_slide=False
        2、在PC端侧边栏打开：pc_slide=True
        """
        encode_url = quote_plus(url)
        if self.pc_slide:
            final_link = 'dingtalk://dingtalkclient/page/link?url={}&pc_slide=true'.format(encode_url)
        else:
            final_link = 'dingtalk://dingtalkclient/page/link?url={}&pc_slide=false'.format(encode_url)
        return final_link        

    def send_text(self, msg, is_at_all=False, at_mobiles=[], at_dingtalk_ids=[], is_auto_at=True):
        """
        text类型
        :param msg: 消息内容
        :param is_at_all: @所有人时：true，否则为false（可选）
        :param at_mobiles: 被@人的手机号（注意：可以在msg内容里自定义@手机号的位置，也支持同时@多个手机号，可选）
        :param at_dingtalk_ids: 被@人的dingtalkId（可选）
        :param is_auto_at: 是否自动在msg内容末尾添加@手机号，默认自动添加，可设置为False取消（可选）
        :return: 返回消息发送结果
        """
        data = {"msgtype": "text", "at": {}}
        if is_not_null_and_blank_str(msg):
            data["text"] = {"content": msg}
        else:
            logger.error("text类型，消息内容不能为空！")
            raise ValueError("text类型，消息内容不能为空！")

        if is_at_all:
            data["at"]["isAtAll"] = is_at_all

        if at_mobiles:
            at_mobiles = list(map(str, at_mobiles))
            data["at"]["atMobiles"] = at_mobiles
            if is_auto_at:
                mobiles_text = '\n@' + '@'.join(at_mobiles)
                data["text"]["content"] = msg + mobiles_text

        if at_dingtalk_ids:
            at_dingtalk_ids = list(map(str, at_dingtalk_ids))
            data["at"]["atDingtalkIds"] = at_dingtalk_ids

        logger.debug('text类型：%s' % data)
        return self.post(data)

    def send_image(self, pic_url):
        """
        image类型（表情）
        :param pic_url: 图片链接
        :return: 返回消息发送结果
        """
        if is_not_null_and_blank_str(pic_url):
            data = {
                "msgtype": "image",
                "image": {
                    "picURL": pic_url
                }
            }
            logger.debug('image类型：%s' % data)
            return self.post(data)
        else:
            logger.error("image类型中图片链接不能为空！")
            raise ValueError("image类型中图片链接不能为空！")

    def send_link(self, title, text, message_url, pic_url=''):
        """
        link类型
        :param title: 消息标题
        :param text: 消息内容（如果太长自动省略显示）
        :param message_url: 点击消息触发的URL
        :param pic_url: 图片URL（可选）
        :return: 返回消息发送结果

        """
        if all(map(is_not_null_and_blank_str, [title, text, message_url])):
            data = {
                    "msgtype": "link",
                    "link": {
                        "text": text,
                        "title": title,
                        "picUrl": pic_url,
                        "messageUrl": self.msg_open_type(message_url)
                    }
            }
            logger.debug('link类型：%s' % data)
            return self.post(data)
        else:
            logger.error("link类型中消息标题或内容或链接不能为空！")
            raise ValueError("link类型中消息标题或内容或链接不能为空！")

    def send_markdown(self, title, text, is_at_all=False, at_mobiles=[], at_dingtalk_ids=[], is_auto_at=True):
        """
        markdown类型
        :param title: 首屏会话透出的展示内容
        :param text: markdown格式的消息内容
        :param is_at_all: @所有人时：true，否则为：false（可选）
        :param at_mobiles: 被@人的手机号（默认自动添加在text内容末尾，可取消自动化添加改为自定义设置，可选）
        :param at_dingtalk_ids: 被@人的dingtalkId（可选）
        :param is_auto_at: 是否自动在text内容末尾添加@手机号，默认自动添加，可设置为False取消（可选）        
        :return: 返回消息发送结果
        """
        if all(map(is_not_null_and_blank_str, [title, text])):
            # 给Mardown文本消息中的跳转链接添加上跳转方式
            text = re.sub(r'(?<!!)\[.*?\]\((.*?)\)', lambda m: m.group(0).replace(m.group(1), self.msg_open_type(m.group(1))), text)
            data = {
                "msgtype": "markdown",
                "markdown": {
                    "title": title,
                    "text": text
                },
                "at": {}
            }
            if is_at_all:
                data["at"]["isAtAll"] = is_at_all

            if at_mobiles:
                at_mobiles = list(map(str, at_mobiles))
                data["at"]["atMobiles"] = at_mobiles
                if is_auto_at:
                    mobiles_text = '\n@' + '@'.join(at_mobiles)
                    data["markdown"]["text"] = text + mobiles_text

            if at_dingtalk_ids:
                at_dingtalk_ids = list(map(str, at_dingtalk_ids))
                data["at"]["atDingtalkIds"] = at_dingtalk_ids

            logger.debug("markdown类型：%s" % data)
            return self.post(data)
        else:
            logger.error("markdown类型中消息标题或内容不能为空！")
            raise ValueError("markdown类型中消息标题或内容不能为空！")

    def send_action_card(self, action_card):
        """
        ActionCard类型
        :param action_card: 整体跳转ActionCard类型实例或独立跳转ActionCard类型实例
        :return: 返回消息发送结果
        """
        if isinstance(action_card, ActionCard):
            data = action_card.get_data()
            
            if "singleURL" in data["actionCard"]:
                data["actionCard"]["singleURL"] = self.msg_open_type(data["actionCard"]["singleURL"])
            elif "btns" in data["actionCard"]:
                for btn in data["actionCard"]["btns"]:
                    btn["actionURL"] = self.msg_open_type(btn["actionURL"])
            
            logger.debug("ActionCard类型：%s" % data)
            return self.post(data)
        else:
            logger.error("ActionCard类型：传入的实例类型不正确，内容为：{}".format(str(action_card)))
            raise TypeError("ActionCard类型：传入的实例类型不正确，内容为：{}".format(str(action_card)))

    def send_feed_card(self, links):
        """
        FeedCard类型
        :param links: FeedLink实例列表 or CardItem实例列表
        :return: 返回消息发送结果
        """
        if not isinstance(links, list):
            logger.error("FeedLink类型：传入的数据格式不正确，内容为：{}".format(str(links)))
            raise ValueError("FeedLink类型：传入的数据格式不正确，内容为：{}".format(str(links)))
        
        link_list = []
        for link in links:
            # 兼容：1、传入FeedLink实例列表；2、CardItem实例列表；
            if isinstance(link, FeedLink) or isinstance(link, CardItem):
                link = link.get_data()
                link['messageURL'] = self.msg_open_type(link['messageURL'])
                link_list.append(link)
            else:
                logger.error("FeedLink类型，传入的数据格式不正确，内容为：{}".format(str(link)))
                raise ValueError("FeedLink类型，传入的数据格式不正确，内容为：{}".format(str(link)))

        
        data = {"msgtype": "feedCard", "feedCard": {"links": link_list}}
        logger.debug("FeedCard类型：%s" % data)
        return self.post(data)

    def post(self, data):
        """
        发送消息（内容UTF-8编码）
        :param data: 消息数据（字典）
        :return: 返回消息发送结果
        """
        now = time.time()
        
        # 钉钉自定义机器人安全设置加签时，签名中的时间戳与请求时不能超过一个小时，所以每个1小时需要更新签名
        if now - self.start_time >= 3600 and self.secret is not None and self.secret.startswith('SEC'):
            self.start_time = now
            self.update_webhook()

        # 钉钉自定义机器人现在每分钟最多发送20条消息
        self.queue.put(now)
        if self.queue.full():
            elapse_time = now - self.queue.get()
            if elapse_time < 60:
                sleep_time = int(60 - elapse_time) + 1
                logger.debug('钉钉官方限制机器人每分钟最多发送20条，当前发送频率已达限制条件，休眠 {}s'.format(str(sleep_time)))
                time.sleep(sleep_time)

        try:
            post_data = json.dumps(data)
            response = requests.post(self.webhook, headers=self.headers, data=post_data)
        except requests.exceptions.HTTPError as exc:
            logger.error("消息发送失败， HTTP error: %d, reason: %s" % (exc.response.status_code, exc.response.reason))
            raise
        except requests.exceptions.ConnectionError:
            logger.error("消息发送失败，HTTP connection error!")
            raise
        except requests.exceptions.Timeout:
            logger.error("消息发送失败，Timeout error!")
            raise
        except requests.exceptions.RequestException:
            logger.error("消息发送失败, Request Exception!")
            raise
        else:
            try:
                result = response.json()
            except JSONDecodeError:
                logger.error("服务器响应异常，状态码：%s，响应内容：%s" % (response.status_code, response.text))
                return {'errcode': 500, 'errmsg': '服务器响应异常'}
            else:
                logger.debug('发送结果：%s' % result)
                # 消息发送失败提醒（errcode 不为 0，表示消息发送异常），默认不提醒，开发者可以根据返回的消息发送结果自行判断和处理
                if self.fail_notice and result.get('errcode', True):
                    time_now = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(time.time()))
                    error_data = {
                      "msgtype": "text",
                      "text": {
                        "content": "[注意-自动通知]钉钉机器人消息发送失败，时间：%s，原因：%s，请及时跟进，谢谢!" % (
                          time_now, result['errmsg'] if result.get('errmsg', False) else '未知异常')
                        },
                      "at": {
                        "isAtAll": False
                        }
                      }
                    logger.error("消息发送失败，自动通知：%s" % error_data)
                    requests.post(self.webhook, headers=self.headers, data=json.dumps(error_data))
                return result


class ActionCard(object):
    """
    ActionCard类型消息格式（整体跳转、独立跳转）
    """
    def __init__(self, title, text, btns, btn_orientation=0, hide_avatar=0):
        """
        ActionCard初始化
        :param title: 首屏会话透出的展示内容
        :param text: markdown格式的消息
        :param btns: 按钮列表：（1）按钮数量为1时，整体跳转ActionCard类型；（2）按钮数量大于1时，独立跳转ActionCard类型；
        :param btn_orientation: 0：按钮竖直排列，1：按钮横向排列（可选）
        :param hide_avatar: 0：正常发消息者头像，1：隐藏发消息者头像（可选）
        """
        super(ActionCard, self).__init__()
        self.title = title
        self.text = text
        self.btn_orientation = btn_orientation
        self.hide_avatar = hide_avatar
        btn_list = []
        for btn in btns:
            if isinstance(btn, CardItem):
                btn_list.append(btn.get_data())
        if btn_list:
            btns = btn_list  # 兼容：1、传入CardItem示例列表；2、传入数据字典列表
        self.btns = btns

    def get_data(self):
        """
        获取ActionCard类型消息数据（字典）
        :return: 返回ActionCard数据
        """
        if all(map(is_not_null_and_blank_str, [self.title, self.text])) and len(self.btns):
            if len(self.btns) == 1:
                # 整体跳转ActionCard类型
                data = {
                        "msgtype": "actionCard",
                        "actionCard": {
                            "title": self.title,
                            "text": self.text,
                            "hideAvatar": self.hide_avatar,
                            "btnOrientation": self.btn_orientation,
                            "singleTitle": self.btns[0]["title"],
                            "singleURL": self.btns[0]["actionURL"]
                        }
                }
                return data
            else:
                # 独立跳转ActionCard类型
                data = {
                    "msgtype": "actionCard",
                    "actionCard": {
                        "title": self.title,
                        "text": self.text,
                        "hideAvatar": self.hide_avatar,
                        "btnOrientation": self.btn_orientation,
                        "btns": self.btns
                    }
                }
                return data
        else:
            logger.error("ActionCard类型，消息标题或内容或按钮数量不能为空！")
            raise ValueError("ActionCard类型，消息标题或内容或按钮数量不能为空！")


class FeedLink(object):
    """
    FeedCard类型单条消息格式
    """
    def __init__(self, title, message_url, pic_url):
        """
        初始化单条消息文本
        :param title: 单条消息文本
        :param message_url: 点击单条信息后触发的URL
        :param pic_url: 点击单条消息后面图片触发的URL
        """
        super(FeedLink, self).__init__()
        self.title = title
        self.message_url = message_url
        self.pic_url = pic_url

    def get_data(self):
        """
        获取FeedLink消息数据（字典）
        :return: 本FeedLink消息的数据
        """
        if all(map(is_not_null_and_blank_str, [self.title, self.message_url, self.pic_url])):
            data = {
                    "title": self.title,
                    "messageURL": self.message_url,
                    "picURL": self.pic_url
            }
            return data
        else:
            logger.error("FeedCard类型单条消息文本、消息链接、图片链接不能为空！")
            raise ValueError("FeedCard类型单条消息文本、消息链接、图片链接不能为空！")


class CardItem(object):
    """
    ActionCard和FeedCard消息类型中的子控件
    
    注意：
    1、发送FeedCard消息时，参数pic_url必须传入参数值；
    2、发送ActionCard消息时，参数pic_url不需要传入参数值；
    """

    def __init__(self, title, url, pic_url=None):
        """
        CardItem初始化
        @param title: 子控件名称
        @param url: 点击子控件时触发的URL
        @param pic_url: FeedCard的图片地址，ActionCard时不需要，故默认为None
        """
        super(CardItem, self).__init__()
        self.title = title
        self.url = url
        self.pic_url = pic_url

    def get_data(self):
        """
        获取CardItem子控件数据（字典）
        @return: 子控件的数据
        """
        if all(map(is_not_null_and_blank_str, [self.title, self.url, self.pic_url])):
            # FeedCard类型
            data = {
                "title": self.title,
                "messageURL": self.url,
                "picURL": self.pic_url
            }
            return data
        elif all(map(is_not_null_and_blank_str, [self.title, self.url])):
            # ActionCard类型
            data = {
                "title":     self.title,
                "actionURL": self.url
            }
            return data
        else:
            logger.error("CardItem是ActionCard的子控件时，title、url不能为空；是FeedCard的子控件时，title、url、pic_url不能为空！")
            raise ValueError("CardItem是ActionCard的子控件时，title、url不能为空；是FeedCard的子控件时，title、url、pic_url不能为空！")

if __name__ == "__main__":
    import os
    # 从环境变量里获取一些敏感配置，比如API 地址，API key
    dingAPI   = os.getenv("DING_API"  , 'https://oapi.dingtalk.com/robot/send')
    dingToken = os.getenv("DING_TOKEN", 'e293cc6ece90cea8f463a1ceeac80f0bd8fb0e8229427ecea5df39c9504cb6d4' )
    dingKeyWd = os.getenv("DING_KEY_WD",'K8S-CI')
    # 获取参数（消息模板的名字）
    if len(sys.argv) < 2:
        print("[\033[1;33mWARNING\033[0m] - 钉钉脚本的参数不对，参考：python3 xx.py msgTmplt")
        sys.exit(0)
    msgTmplt   = sys.argv[1]
    msgAtMmbr  = list(set(os.getenv("CI_DINGDING_AT","UserNameNotFound").strip("__").split("__")))
    crrntMdl   = os.getenv("CI_CODE_PROJECT")
    buildUrl   = os.getenv("BUILD_URL")

    # 定义消息模板
    msgTmplts = {
        "cinotice":{
            "title"      : "[{}] {} 构建消息".format(os.getenv("DING_KEY_WD"),crrntMdl),
            "text"       : ["### {} 构建消息".format(crrntMdl),
                            "---",
                            "**【代码分支】**：{}".format(os.getenv("CI_CODE_BRANCH")),
                            "**【由谁触发】**：{}".format(os.getenv("CI_CODE_DEVELOPER")),
                            "**【提交哈希】**：{}".format(os.getenv("CI_CODE_COMMIT_SHA_SHORT")),
                            "**【提交信息】**：{}".format(os.getenv("CI_CODE_COMMIT_MSG")),
                            "---",
                            os.getenv("CI_PIPELINE_STAGE_MSG"),
                            "---",
                            "- [点此查看Jenkins构建日志]({}console)".format(os.getenv("BUILD_URL")),
                            "---"
                           ],
            "at_mobiles" : msgAtMmbr,
            "is_at_all"  : False
        },
    }
    if msgTmplt not in msgTmplts:
        print("[\033[1;33mWARNING\033[0m] - 钉钉脚本参数错误，消息模板不存在，可用的有：{}".format(msgTmplts.keys()))
        sys.exit(0)
    # 创建钉钉机器人客户端
    try:
        dingRobot = DingtalkChatbot('{}?access_token={}'.format(dingAPI,dingToken))
        msgTmplt  = msgTmplts[ msgTmplt ]
        msgText   = "\n\n".join(map(lambda x:str(x), msgTmplt["text"]))
        dingRobot.send_markdown( msgTmplt["title"], "{}\n\n".format(msgText), is_at_all=msgTmplt["is_at_all"], at_mobiles=msgTmplt["at_mobiles"])
    except Exception as e:
        print("[\033[1;33mWARNING\033[0m] - 钉钉通知失败: {}".format(str(e)))