package iuh.fit.auditservice.infrastructure.persistence.repository.converter;

import com.turkraft.springfilter.parser.node.FilterNode;
import com.turkraft.springfilter.parser.node.InfixOperationNode;
import com.turkraft.springfilter.parser.node.FieldNode;
import com.turkraft.springfilter.parser.node.InputNode;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.regex.Pattern;

@Component
public class MongoCriteriaConverter {

    public Query convert(FilterNode node) {
        Query query = new Query();
        Criteria criteria = visit(node);

        if (criteria != null) {
            query.addCriteria(criteria);
        }

        return query;
    }

    private Criteria visit(FilterNode node) {
        if (node == null) {
            return null;
        }

        if (node instanceof InfixOperationNode infixNode) {
            String op = getOperatorName(infixNode);

            if (op == null) {
                return null;
            }

            op = op.toLowerCase();

            FilterNode left = infixNode.getLeft();
            FilterNode right = infixNode.getRight();

            if ("and".equals(op)) {
                Criteria leftCriteria = visit(left);
                Criteria rightCriteria = visit(right);

                if (leftCriteria != null && rightCriteria != null) {
                    return new Criteria().andOperator(leftCriteria, rightCriteria);
                } else if (leftCriteria != null) {
                    return leftCriteria;
                } else {
                    return rightCriteria;
                }
            } else if ("or".equals(op)) {
                Criteria leftCriteria = visit(left);
                Criteria rightCriteria = visit(right);

                if (leftCriteria != null && rightCriteria != null) {
                    return new Criteria().orOperator(leftCriteria, rightCriteria);
                } else if (leftCriteria != null) {
                    return leftCriteria;
                } else {
                    return rightCriteria;
                }
            }

            if (left instanceof FieldNode fieldNode) {
                String fieldPath = getFieldName(fieldNode);
                Object value = getInputValue(right);

                if ("statusCode".equals(fieldPath) && value != null) {
                    if (value instanceof Number num) {
                        value = num.intValue();
                    } else {
                        try {
                            value = Integer.parseInt(value.toString().trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                if ("timestamp".equals(fieldPath) && value != null) {
                    try {
                        value = Instant.parse(value.toString().trim());
                    } catch (Exception ignored) {
                    }
                }

                switch (op) {
                    case ":":
                    case "==":
                    case "=":
                        return Criteria.where(fieldPath).is(value);
                    case "!=":
                        return Criteria.where(fieldPath).ne(value);
                    case ">":
                        assert value != null;
                        return Criteria.where(fieldPath).gt(value);
                    case ">=":
                        assert value != null;
                        return Criteria.where(fieldPath).gte(value);
                    case "<":
                        assert value != null;
                        return Criteria.where(fieldPath).lt(value);
                    case "<=":
                        assert value != null;
                        return Criteria.where(fieldPath).lte(value);
                    case "like":
                        if (value != null) {
                            return Criteria.where(fieldPath).regex(Pattern.compile(value.toString(), Pattern.CASE_INSENSITIVE));
                        }
                        break;
                    case "in":
                        if (value instanceof Collection<?> col) {
                            return Criteria.where(fieldPath).in(col);
                        } else if (value != null) {
                            return Criteria.where(fieldPath).in(value);
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        if (node.getClass().getSimpleName().contains("PostfixOperationNode") || node.getClass().getSimpleName().contains("Postfix")) {
            String op = "";
            try {
                Object operatorObj = node.getClass().getMethod("getOperator").invoke(node);
                java.lang.reflect.Method getName = operatorObj.getClass().getMethod("getName");
                op = (String) getName.invoke(operatorObj);
            } catch (Exception e) {
                try {
                    Object operatorObj = node.getClass().getMethod("getOperator").invoke(node);
                    op = operatorObj.toString();
                } catch (Exception ignored) {
                }
            }

            FilterNode operand = null;
            try {
                operand = (FilterNode) node.getClass().getMethod("getLeft").invoke(node);
            } catch (Exception e) {
                try {
                    operand = (FilterNode) node.getClass().getMethod("getOperand").invoke(node);
                } catch (Exception ignored) {
                }
            }

            if (operand instanceof FieldNode fieldNode) {
                String fieldPath = getFieldName(fieldNode);
                if (op != null) {
                    op = op.toLowerCase();
                    if (op.contains("null")) {
                        return Criteria.where(fieldPath).is(null);
                    }
                }
            }
        }

        return null;
    }

    private String getOperatorName(InfixOperationNode node) {
        try {
            Object opObj = node.getOperator();

            try {
                return (String) opObj.getClass().getMethod("getName").invoke(opObj);
            } catch (Exception e) {
                return opObj.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String getFieldName(FieldNode node) {
        try {
            return (String) node.getClass().getMethod("getName").invoke(node);
        } catch (Exception e) {
            try {
                return (String) node.getClass().getMethod("getField").invoke(node);
            } catch (Exception ex) {
                return node.toString();
            }
        }
    }

    private Object getInputValue(FilterNode node) {
        if (node instanceof InputNode inputNode) {
            try {
                return inputNode.getClass().getMethod("getValue").invoke(inputNode);
            } catch (Exception e) {
                try {
                    return inputNode.getClass().getMethod("getConstant").invoke(inputNode);
                } catch (Exception ex) {
                    return inputNode.toString();
                }
            }
        }
        return null;
    }
}
