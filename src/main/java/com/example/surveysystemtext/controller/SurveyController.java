package com.example.surveysystemtext.controller;

import com.example.surveysystemtext.controller.vo.GPTGenerateRespVO;
import com.example.surveysystemtext.entity.*;
import com.example.surveysystemtext.mapper.OptionMapper;
import com.example.surveysystemtext.mapper.QuestionMapper;
import com.example.surveysystemtext.mapper.SurveyMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plexpt.chatgpt.ChatGPT;
import com.plexpt.chatgpt.entity.chat.ChatCompletion;
import com.plexpt.chatgpt.entity.chat.ChatCompletionResponse;
import com.plexpt.chatgpt.entity.chat.Message;
import com.plexpt.chatgpt.util.Proxys;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/Survey")
public class SurveyController {
    Proxy proxy = Proxys.http("127.0.0.1", 7890);
    @Autowired
    private SurveyMapper surveyMapper;
    @Resource
    private OptionMapper optionMapper;
    @Resource
    private QuestionMapper questionMapper;
    @Value("${chatgpt.apiKey}")
    private String apiKey;
    @Value("${chatgpt.apiHost}")
    private String apiHost;


    @GetMapping("/FindAllSurvey")//根据id查询所有问卷信息
    public CommonResult<List<Survey>> findAllSurvey() {
        return new CommonResult<>(surveyMapper.findAllSurvey());
    }


    @PostMapping("/CreateTemplate") // 通过问卷生成模板
    public CommonResult<Survey> CreateTemplate(@RequestBody Survey survey) {
        // 通过surveyId查找问卷
        survey = surveyMapper.selectById(survey.getSurveyId());
        Survey oldSurvey = surveyMapper.FindAllSurveyInfo(survey);
        if (oldSurvey == null) {
            return new CommonResult<>(400, "生成模板失败，问卷不存在");
        }
        /*
         *  将oldSurvey的所有属性复制给survey
         */
        // 设置新模板属性
        survey.setCreatedUserId(1L);
        survey.setSurveyId(null); // 设置为null以实现自增
        survey.setState(4); // 设置为4
        survey.setAnalysis(null); // 去掉原有分析
        survey.setRemark(survey.getRemark()); // 设置备注
        survey.setSurveyTitle(oldSurvey.getSurveyTitle()); // 设置标题
        // 插入新模板
        surveyMapper.insert(survey);
        /*
         * 通过oldsurveyId遍历Question， 然后将获取的question存入到questionList中
         */

        var sid = survey.getSurveyId(); // 获得新模板的id
        /*
         *  将oldSurvey进行插入到新模板中
         */
        List<Question> questionList = oldSurvey.getQuestionList();
        if (questionList != null) {
            for (var q : questionList) {
                q.setSurveyId(sid);
                q.setQuestionId(null);
                questionMapper.insert(q);
                var newQuestion = questionMapper.selectById(q.getQuestionId()); // 返回新插入的问题对象
                var qid = newQuestion.getQuestionId(); // 获得新问题的id
                List<Option> optionList = q.getOptionList();
                if (optionList != null) {
                    for (var o : optionList) {
                        if (q.getType() != 1 && q.getType() != 2)
                            break;
                        o.setQuestionId(qid);
                        o.setOptionId(null);
                        optionMapper.insert(o);
                    }
                }
            }

        }
        survey.setQuestionList(questionList); // 设置新问题列表
        return new CommonResult<>(survey);
    }


    @PostMapping("/InsertSurvey")//添加问卷 输入完整问卷信息
    public CommonResult<Survey> InsertSurvey(@RequestBody Survey survey) {
        var ssid = survey.getSurveyId();
        if (surveyMapper.selectById(ssid) != null) {
            return new CommonResult<>(400, "添加失败，问卷已存在");
        }
        surveyMapper.insert(survey);
        var sid = survey.getSurveyId();
        /*
         * 插入所有问题
         */
        List<Question> questionList = survey.getQuestionList();
        if (questionList != null) {
            for (var q : questionList) {
                /*
                 * 插入所有选项
                 */
                q.setSurveyId(sid);
                questionMapper.insert(q);
                var qid = q.getQuestionId();
                var optionList = q.getOptionList();
                if (optionList != null) {
                    for (var o : optionList) {
                        o.setQuestionId(qid);
                        optionMapper.insert(o);
                    }
                }
            }
        }
        return new CommonResult<>(survey);
    }

    @PostMapping("/UpdataSurvey")//修改问卷信息 输入完整问卷信息
    public CommonResult<Survey> UpdateSurvey(@RequestBody Survey survey) {
        var sid = survey.getSurveyId();
        if (surveyMapper.selectById(sid) == null) {
            return new CommonResult<>(400, "更新失败，问卷不存在");
        }
        DeleteSurveyById(survey);
        InsertSurvey(survey);
        Survey survey1 = surveyMapper.FindAllSurveyInfo(survey);
        return new CommonResult<>(survey1);
    }

    @PostMapping("/FindSurveyInfoById")//根据id查询问卷的所有问题和选项
    public CommonResult<Survey> FindSurveyInfoById(@RequestBody Survey survey) {
        var sid = survey.getSurveyId();
        if (surveyMapper.selectById(sid) == null) {
            return new CommonResult<>(400, "查询失败，问卷不存在");
        }
        return new CommonResult<>(surveyMapper.FindAllSurveyInfo(survey));
    }

    @PostMapping("/DeleteSurveyById")//根据问卷id删除整份问卷
    public CommonResult<Survey> DeleteSurveyById(@RequestBody Survey survey) {
        var sid = survey.getSurveyId();
        if (surveyMapper.selectById(sid) == null) {
            return new CommonResult<>(400, "删除失败，问卷不存在");
        }
        /*
         * 获取原有的问卷信息
         */
        Survey survey1 = surveyMapper.FindAllSurveyInfo(survey);
        List<Question> questionList1 = survey1.getQuestionList();
        surveyMapper.deleteById(survey);
        /*
         * 删除原有问卷问题
         */
        if (questionList1 != null) {
            for (var q1 : questionList1) {
                List<Option> optionList1 = q1.getOptionList();
                questionMapper.deleteById(q1);
                /*
                 * 删除原有问题选项
                 */
                if (optionList1 != null) {
                    for (var o1 : optionList1) {
                        optionMapper.deleteById(o1);
                    }
                }
            }
        }
        return new CommonResult<>(0, "删除成功");
    }

    @PostMapping("/UpdateSurveyState") //根据问卷id修改问卷状态
    public CommonResult<Survey> UpdateSurveyStatus(@RequestBody Survey survey) {
        var sid = survey.getSurveyId();
        if (surveyMapper.selectById(sid) == null) {
            return new CommonResult<>(400, "修改失败，问卷不存在");
        }
        surveyMapper.updateById(survey);
        return new CommonResult<>(survey);
    }

    @PostMapping("/GetSurveyAnalysis") //根据问卷id获取问卷分析
    public CommonResult<DataMessage> GetSurveyAnalysis(@RequestBody Survey survey) {
        String re = "";
        Survey survey1 = surveyMapper.FindAllSurveyInfo(survey);
        if (survey1 == null) {
            return new CommonResult<>(401, "获取失败");
        }
        List<Question> questionList = survey1.getQuestionList();
        if (questionList == null) {
            return new CommonResult<>(400, "获取失败");
        }
        for (var question : questionList) {
            if (question.getType() <= 2) {
                re = re + "{title:'" + question.getQuestion() + "',";
                var optionList = question.getOptionList();
                if (optionList == null) {
                    return new CommonResult<>(400, "获取失败");
                }
                re = re + "optionList: ";
                for (var option : question.getOptionList()) {
                    re = re + "{text:'" + option.getContent() + "'," + "fill_num:" + option.getCount() + "},";
                }
                re = re + "},";
            }
        }
        DataMessage dataMessage = new DataMessage();
        dataMessage.setPrompt(re);


        ChatGPT chatGPT = ChatGPT.builder()
                .timeout(600)
                .apiKey(apiKey)
                .proxy(proxy)
                .apiHost(apiHost)
                .build()
                .init();

        Message message = Message.of("给你一段json格式的数据，这是份问卷的数据，帮我分析下列给出的数据并且以自然语言去描述，请对该问卷进行分析总结，字数100字以内，以下是给出的数据：" + dataMessage.getPrompt());
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model(ChatCompletion.Model.GPT_3_5_TURBO.getName())
                .messages((List.of(message)))
                .maxTokens(3000)
                .temperature(0.9)
                .build();

        ChatCompletionResponse response = chatGPT.chatCompletion(chatCompletion);
        Message result = response.getChoices().get(0).getMessage();
        dataMessage.setPrompt(result.getContent());
        survey1.setAnalysis(result.getContent());
        surveyMapper.updateById(survey1);
        return new CommonResult<>(dataMessage);
    }

    @PostMapping("/generateSurvey")//生成整份问卷
    @SneakyThrows
    public CommonResult<Survey> generateSurvey(@RequestBody DataMessage dataMessage) {

        ChatGPT chatGPT = ChatGPT.builder()
                .timeout(600)
                .apiKey(apiKey)
                .proxy(proxy)
                .apiHost(apiHost)
                .build()
                .init();

        Message system = Message.ofSystem(String.format("""
                请你根据我输入给你的问卷主题随机生成一份调查问卷，其中包括问卷的所有题目，问卷中只有选择题，题目数量输出%d题，并用JSON的方式完成输入输出，使用下面的格式完成输入输出：
                输入:
                 {"keyword": "大学生就业情况调查"}
                输出:
                 {"questionList":[{"title":"目前就读的专业类型","optionList":[{"text":"理工科"},{"text":"文史类"},{"text":"医学类"},{"text":"艺术类"},{"text":"其他"}]}]}
                 
                输入:
                 {"keyword": "%s"}
                输出:
                 """, dataMessage.getCount(),dataMessage.getPrompt()));
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model(ChatCompletion.Model.GPT_3_5_TURBO.getName())
                .messages(List.of(system))
                .maxTokens(3000)
                .temperature(0.9)
                .build();

        ChatCompletionResponse response = chatGPT.chatCompletion(chatCompletion);
        Message result = response.getChoices().get(0).getMessage();
        ObjectMapper mapper = new ObjectMapper();
        GPTGenerateRespVO respVO;
        try {
            respVO = mapper.readValue(result.getContent(), GPTGenerateRespVO.class);
        } catch (Exception e) {
            System.out.println("解析失败");
            e.printStackTrace();
            return new CommonResult<>(400, "生成失败");
        }
        /*
         * 将返回的数据类型转换成survey实体
         */
        int order = 1;
        Survey survey = new Survey();
        List<Question> sqlist = new ArrayList<>();
        for(var q : respVO.getQuestionList()){
            Question sq = new Question();
            sq.setQuestion(q.getTitle());
            sq.setQuestionOrder(order);
            order = order + 1;
            List<Option> solist = new ArrayList<>();
            for(var o : q.getOptionList()){
                Option so = new Option();
                so.setContent(o.getText());
                solist.add(so);
            }
            sq.setOptionList(solist);
            sqlist.add(sq);
        }
        survey.setQuestionList(sqlist);
        survey.setSurveyTitle(dataMessage.getPrompt());
        survey.setCreatedUserId(-1L);
        InsertSurvey(survey);
        Survey survey1 = surveyMapper.FindAllSurveyInfo(survey);
        if(survey1.getSurveyId() == null){
            return new CommonResult<>(400, "创建失败");
        }
        return new CommonResult<>(survey1);
    }
}